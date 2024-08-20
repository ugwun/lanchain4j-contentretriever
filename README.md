# LangChain4J Spring Boot ContentRetriever Tutorial

This tutorial demonstrates how to implement LangChain4J ContentRetriever in a Spring Boot application. The concepts covered can be applied to any RAG (Retrieval-Augmented Generation) paradigm.

## Background

While developing our Spring Boot LangChain4J-powered application, Advanced Coding Assistant, we realized the need for granular control over what an LLM can retrieve from the Neo4J database.

The out-of-the-box Neo4jContentRetriever from LangChain4J works well for smaller use cases. However, as the Knowledge Graph in the Neo4J DB grows with nodes and edges not directly associated with the information required for your RAG use case, the LLM can become confused.

Moreover, potential security issues arise when an LLM has direct access to your DB schema, as it could potentially extract and display sensitive information, such as user data stored in the DB.

To address these concerns, we need more control. All the code for this tutorial is available in the repository.

## Main Entry Point

The main entry point for testing is:

```
com.example.langchain4jcontentretriever.Langchain4JContentRetrieverApplicationTests#testFindActorsByMovieTitleUsingLLM
```

## Movie Domain Setup

The demo application deals with movies and actors, containing two types of nodes:

### Node Labels:
1. MovieNode
2. ActorNode

### Properties:
1. MovieNode:
    - title: String (ID)
    - dateCreated: LocalDate

2. ActorNode:
    - name: String (ID)

### Relationships:
1. (ActorNode)-[:PLAYED_IN]->(MovieNode)

An actor is associated with a movie via the PLAYED_IN edge.

## Repositories

Using Spring Data Neo4j, we implement two repositories: MovieRepository and ActorRepository

### MovieRepository

```java
@Repository
public interface MovieRepository extends Neo4jRepository<MovieNode, String> {
    MovieNode findByTitle(String title);

    @Query("MATCH (m:MovieNode)<-[:PLAYED_IN]-(a:ActorNode {name: $actorName}) RETURN m")
    List<MovieNode> findMoviesByActorName(@Param("actorName") String actorName);

    @Query("MATCH (m:MovieNode) WHERE m.dateCreated.year = $year RETURN m")
    List<MovieNode> findMoviesByYear(@Param("year") int year);

    @Query("MATCH (m:MovieNode) WHERE m.title =~ ('(?i).*' + $titlePart + '.*') RETURN m")
    List<MovieNode> findMoviesByTitleContaining(@Param("titlePart") String titlePart);

    @Query("MATCH (m:MovieNode {title: $movieTitle})<-[:PLAYED_IN]-(a:ActorNode) RETURN count(a)")
    int countActorsInMovie(@Param("movieTitle") String movieTitle);
}
```

### ActorRepository

```java
@Repository
public interface ActorRepository extends Neo4jRepository<ActorNode, String> {
    @Query("MATCH (a:ActorNode)-[:PLAYED_IN]->(m:MovieNode {title: $movieTitle}) RETURN a")
    List<ActorNode> findActorsByMovieTitle(@Param("movieTitle") String movieTitle);
}
```

These repositories will be used later by MyContentRetriever to deterministically retrieve information from the Neo4J DB.

## Setup Neo4jContentRetriever for Comparison

Setting up LangChain4J Neo4jContentRetriever is straightforward:

```java
Neo4jContentRetriever.builder()
                .graph(neo4jGraph)
                .chatLanguageModel(chatLanguageModel)
                .promptTemplate(PROMPT_TEMPLATE)
                .build();
```

Find details here:
`com.example.langchain4jcontentretriever.AppConfig#neo4jAstContentRetriever`

We use DefaultRetrievalAugmentor to route queries so that if a user asks non-movie-related questions, the LLM will not access the DB:

```
"Does it make sense to search the movie and actor database " +
"and extract additional information based on the provided user query?" +
"It's vital for you to search the database regarding any questions" +
"which are associated in any way with a movie and/or actor. " +
"Answer only 'yes' or 'no'. " +
"Query: {{it}}"
```

Details can be found here:
`com.example.langchain4jcontentretriever.AppConfig#retrievalAugmentor`

To better understand how RetrievalAugmentors work, refer to this diagram:
[source: langchain4j.dev]

## Setup Proprietary ContentRetriever: MyContentRetriever

```java
@Component
public class MyContentRetriever implements ContentRetriever {

    private final ChatLanguageModel chatLanguageModel;
    private final ActorRepository actorRepository;
    private final MovieRepository movieRepository;

    public MyContentRetriever(ChatLanguageModel chatLanguageModel,
                              ActorRepository actorRepository,
                              MovieRepository movieRepository) {
        this.chatLanguageModel = chatLanguageModel;
        this.actorRepository = actorRepository;
        this.movieRepository = movieRepository;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String question = query.text();
        String movieTitle = chatLanguageModel.generate("Extract movie name/title from this question: '" + question + "'. "
                + "It is imperative that you return only the movie name and nothing else");
        String queryNumber = chatLanguageModel.generate("You have access to two queries: " +
                "1. findActorsByMovieTitle 2. countActorsInMovie. 3. other" +
                "Based on the question return either number 1 or 2. or 3. Question: '" + question + "'");

        if (queryNumber.contains("1")) {
            List<ActorNode> actorsByMovieTitle = actorRepository.findActorsByMovieTitle(movieTitle);
            return actorsByMovieTitle.stream()
                    .map(actorNode -> Content.from(actorNode.getName()))
                    .toList();
        } else if (queryNumber.contains("2")) {
            int movieCount = movieRepository.countActorsInMovie(movieTitle);
            return List.of(Content.from(String.valueOf(movieCount)));
        } else {
            throw new UnsupportedOperationException("***** The LLM tried to make an illegal operation!");
        }
    }
}
```

Note that we create a Spring component from MyContentRetriever, which allows us to inject repositories: ActorRepository and MovieRepository.

Using the injected ChatLanguageModel, we can filter and extract information from user questions using an LLM. We're primarily interested in the movie title, as we only support questions about movie titles in this case:

```java
String movieTitle = chatLanguageModel.generate("Extract movie name/title from this question: '" + question + "'. "
+ "It is imperative that you return only the movie name and nothing else");
```

We support three operations:
1. Find actors by movie title
2. Count actors in movie
3. Other

We let the LLM decide which operation to take:

```java
String queryNumber = chatLanguageModel.generate("You have access to two queries: " +
"1. findActorsByMovieTitle 2. countActorsInMovie. 3. other" +
"Based on the question return either number 1 or 2. or 3. Question: '" + question + "'");
```

Based on the operation the user wants to perform and the extracted movie title, we then directly use an injected repository instead of letting the LLM construct and execute the query (as done within Neo4jContentRetriever).

If the LLM decides that the question is unrelated to movies, the application will automatically throw an exception.

This approach gives us much more control over what the user and, more importantly, what the LLM can do. If the LLM can't decide which operation to choose or if no movie title is provided, the query cannot continue.

## Additional Setup

- You must have Docker installed, as we're using Neo4jContainer.
- You need access to an LLM API (OpenAI, Azure OpenAI, Vertex, Local LLM, AWS Bedrock, etc.) and set up the correct LLM provider here:

  `com.example.langchain4jcontentretriever.AppConfig#openAiChatModel`

To set up an LLM provider, please refer to the LangChain4J documentation: https://docs.langchain4j.dev/category/language-models

The default is set to the OpenAI provider, but remember, you need at least an API KEY.

## Experiment: Neo4jContentRetriever vs MyContentRetriever

Let's compare these two retrievers.

The test that runs this experiment is here:
`com.example.langchain4jcontentretriever.Langchain4JContentRetrieverApplicationTests#testFindActorsByMovieTitleUsingLLM`

To choose between Neo4jContentRetriever and MyContentRetriever, adapt this bean based on the provided comments within the code:

`com.example.langchain4jcontentretriever.AppConfig#retrievalAugmentor`

```java
@Bean
RetrievalAugmentor retrievalAugmentor(ChatLanguageModel chatLanguageModel,
        // Uncomment this line if you want to try to query the DB with Langchain4J's out of the box retriever
//                                      Neo4jContentRetriever neo4jAstContentRetriever,
        // Comment out this line if you want to try to query the DB with Langchain4J's out of the box retriever
                                      MyContentRetriever myContentRetriever) {
    QueryRouter queryRouter = new QueryRouter() {
        // Don't route the query to the DB if the question is not related to movies
        private final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from(
                "Does it make sense to search the movie and actor database " +
                        "and extract additional information based on the provided user query?" +
                        "It's vital for you to search the database regarding any questions" +
                        "which are associated in any way with a movie and/or actor. " +
                        "Answer only 'yes' or 'no'. " +
                        "Query: {{it}}"
        );

        @Override
        public Collection<ContentRetriever> route(Query query) {
            Prompt prompt = PROMPT_TEMPLATE.apply(query.text());
            AiMessage aiMessage = chatLanguageModel.generate(prompt.toUserMessage()).content();
            System.out.println("***** Neo4J will be queried: " + aiMessage.text().toLowerCase());

            if (aiMessage.text().toLowerCase().contains("no")) {
                return emptyList();
            }
            // Uncomment this line if you want to try to query the DB with Langchain4J's out of the box retriever
//            return singletonList(neo4jAstContentRetriever);
            // Comment out this line if you want to try to query the DB with Langchain4J's out of the box retriever
            return singletonList(myContentRetriever);
        }
    };
```

### Results with Neo4jContentRetriever:

```
***** User says: Hi
***** Neo4J will be queried: no
***** LLM responded: Hello! How can I assist you today?
***** User says: Which actors played in the movie 'Alpha-Centauri Adventures'?
***** Neo4J will be queried: yes
***** LLM responded: The actors who played in the movie titled 'Alpha-Centauri Adventures' are Marcus Delacroix and Sophia Chen.
***** User says: How many actors played in the movie 'Alpha-Centauri Adventures'?
***** Neo4J will be queried: yes
***** LLM responded: Two actors played in the movie 'Alpha-Centauri Adventures.'
***** User says: Show me all actors in the database!
***** Neo4J will be queried: yes
***** LLM responded: Here are all the actors in the database:

- Olivia Sinclair
- Ethan Hawthorne
- Sophia Chen
- Marcus Delacroix
- Amelia Blackwood
- Gabriel Rossi
```

As you can see, when faced with an adversarial query from the user "Show me all actors in the database!", the LLM obliged and extracted all actors from the DB.

### Results with MyContentRetriever:

```
***** User says: Hi
***** Neo4J will be queried: no
***** LLM responded: Hello! How can I assist you today?
***** User says: Which actors played in the movie 'Alpha-Centauri Adventures'?
***** Neo4J will be queried: yes
***** LLM responded: The actors who played in the movie titled 'Alpha-Centauri Adventures' are Sophia Chen and Marcus Delacroix.
***** User says: How many actors played in the movie 'Alpha-Centauri Adventures'?
***** Neo4J will be queried: yes
***** LLM responded: Two actors played in the movie 'Alpha-Centauri Adventures'.
***** User says: Show me all actors in the database!
***** Neo4J will be queried: yes

java.util.concurrent.CompletionException: java.lang.UnsupportedOperationException: ***** The LLM tried to make an illegal operation!

 at java.base/java.util.concurrent.CompletableFuture.encodeThrowable(CompletableFuture.java:315)
 at java.base/java.util.concurrent.CompletableFuture.completeThrowable(CompletableFuture.java:320)
 at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(CompletableFuture.java:1770)
 at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
 at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
 at java.base/java.lang.Thread.run(Thread.java:1570)
Caused by: java.lang.UnsupportedOperationException: ***** The LLM tried to make an illegal operation!
 at com.example.langchain4jcontentretriever.MyContentRetriever.retrieve(MyContentRetriever.java:47)
```

As you can see, with our own implementation of ContentRetriever, we have far more granular control over what the LLM does and how it works with information.
