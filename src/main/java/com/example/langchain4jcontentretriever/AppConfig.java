package com.example.langchain4jcontentretriever;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.neo4j.Neo4jContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Configuration
public class AppConfig {

    @Bean
    public OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey("OPENAI_API_KEY")
                .build();
    }

    @Bean
    Neo4jContentRetriever neo4jAstContentRetriever(Driver neo4jDriver, ChatLanguageModel chatLanguageModel) {
        // This very specific prompt has to be provided about the Neo4J DB schema, otherwise the schema won't
        // get injected. Reason: when the DB is initialized via a Container, it doesn't yet have information schema.
        // Schema information in the DB will be updated when after the first inserts in the test, however this bean
        // has already been constructed without schema information
        final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from("""
        Task: Generate Accurate Cypher Statement to Query a Graph Database About a Movie Database.
        
        Node Labels:
        1. MovieNode
        2. ActorNode
        
        Properties:
        1. MovieNode:
           - title: String (ID)
           - dateCreated: LocalDate
        
        2. ActorNode:
           - name: String (ID)
        
        Relationships:
        1. (ActorNode)-[:PLAYED_IN]->(MovieNode)
        
        Schema:
        (ActorNode {name: String})
        (MovieNode {title: String, dateCreated: LocalDate})
        (ActorNode)-[:PLAYED_IN]->(MovieNode)
        
        ***Example usage***
        Find all actors who played in a specific movie:
        MATCH (a:ActorNode)-[:PLAYED_IN]->(m:MovieNode {title: "Movie Title"})
        RETURN a.name
        
        Find all movies an actor has played in:
        MATCH (a:ActorNode {name: "Actor Name"})-[:PLAYED_IN]->(m:MovieNode)
        RETURN m.title, m.dateCreated
        
        Find movies released in a specific year:
        MATCH (m:MovieNode)
        WHERE m.dateCreated.year = 2023
        RETURN m.title
        
        Find actors who have worked together in the same movie:
        MATCH (a1:ActorNode)-[:PLAYED_IN]->(m:MovieNode)<-[:PLAYED_IN]-(a2:ActorNode)
        WHERE a1.name < a2.name  // To avoid duplicates
        RETURN a1.name, a2.name, m.title
        
        Count the number of movies each actor has played in:
        MATCH (a:ActorNode)-[:PLAYED_IN]->(m:MovieNode)
        RETURN a.name, COUNT(m) AS movieCount
        ORDER BY movieCount DESC
        
        The user question is:
        {{question}}
        \s""");

        Neo4jGraph neo4jGraph = new Neo4jGraph(neo4jDriver);

        neo4jGraph.refreshSchema();

        return Neo4jContentRetriever.builder()
                .graph(neo4jGraph)
                .chatLanguageModel(chatLanguageModel)
                .promptTemplate(PROMPT_TEMPLATE)
                .build();
    }

    @Bean
    RetrievalAugmentor retrievalAugmentor(ChatLanguageModel chatLanguageModel,
            // Uncomment this line if you want to try to query the DB with Langchain4J's out of the box retriever
//                                          Neo4jContentRetriever neo4jAstContentRetriever,
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
//                return singletonList(neo4jAstContentRetriever);
                // Comment out this line if you want to try to query the DB with Langchain4J's out of the box retriever
                return singletonList(myContentRetriever);
            }
        };

        return DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();
    }

    @Bean
    Assistant assistant(ChatLanguageModel chatLanguageModel, RetrievalAugmentor retrievalAugmentor) {
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .retrievalAugmentor(retrievalAugmentor)
                .build();
    }
}
