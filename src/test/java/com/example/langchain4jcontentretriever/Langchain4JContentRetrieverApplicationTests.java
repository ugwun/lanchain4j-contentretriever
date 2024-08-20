package com.example.langchain4jcontentretriever;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.Neo4jContainer;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class Langchain4JContentRetrieverApplicationTests {

    @Autowired
    private ActorRepository actorRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private Assistant assistant;

    @Test
    void contextLoads() {
    }

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
        actorRepository.deleteAll();

        // Made up movies - we want to be sure that the LLM hasn't been trained on the information, therefore we need
        // made up names
        MovieNode movie1 = new MovieNode("Pancake with Lemons", LocalDate.of(1994, 9, 22));
        MovieNode movie2 = new MovieNode("Alpha-Centauri Adventures", LocalDate.of(1972, 3, 24));
        MovieNode movie3 = new MovieNode("Barking Dogs", LocalDate.of(1994, 10, 14));

        // Made up actors  - we want to be sure that the LLM hasn't been trained on the information, therefore we need
        // made up names
        ActorNode actor1 = new ActorNode("Ethan Hawthorne");
        ActorNode actor2 = new ActorNode("Olivia Sinclair");
        ActorNode actor3 = new ActorNode("Marcus Delacroix");
        ActorNode actor4 = new ActorNode("Sophia Chen");
        ActorNode actor5 = new ActorNode("Gabriel Rossi");
        ActorNode actor6 = new ActorNode("Amelia Blackwood");

        // Establish relationships
        movie1.addActor(actor1);
        movie1.addActor(actor2);
        movie2.addActor(actor3);
        movie2.addActor(actor4);
        movie3.addActor(actor5);
        movie3.addActor(actor6);

        List<MovieNode> movies = Arrays.asList(movie1, movie2, movie3);
        List<ActorNode> actors = Arrays.asList(actor1, actor2, actor3, actor4, actor5, actor6);

        movieRepository.saveAll(movies);
        actorRepository.saveAll(actors);
    }

    /**
     * Smoke test that the information about movies and actors is actually stored in the DB
     */
    @Test
    void testFindActorsByMovieTitleUsingRepository() {
        List<ActorNode> shawshankActors = actorRepository.findActorsByMovieTitle("Pancake with Lemons");
        assertEquals(2, shawshankActors.size());
        assertTrue(shawshankActors.stream().anyMatch(actor -> actor.getName().equals("Ethan Hawthorne")));
        assertTrue(shawshankActors.stream().anyMatch(actor -> actor.getName().equals("Olivia Sinclair")));
    }

    /**
     * This test either routes the user request to {@link MyContentRetriever} or {@link dev.langchain4j.rag.content.retriever.neo4j.Neo4jContentRetriever}
     * See {@link AppConfig#retrievalAugmentor(ChatLanguageModel, MyContentRetriever)} `retrievalAugmentor` bean for more details on how to change which Retriever is used
     */
    @Test
    void testFindActorsByMovieTitleUsingLLM() {
        System.out.println("***** User says: Hi");
        String llmResponse = assistant.chat("Hi");

        System.out.println("***** LLM responded: " + llmResponse);

        System.out.println("***** User says: Which actors played in the movie 'Alpha-Centauri Adventures'?");
        llmResponse = assistant.chat("Which actors played in the movie titled 'Alpha-Centauri Adventures'?");

        System.out.println("***** LLM responded: " + llmResponse);

        System.out.println("***** User says: How many actors played in the movie 'Alpha-Centauri Adventures'?");
        llmResponse = assistant.chat("How many actors played in the movie 'Alpha-Centauri Adventures'?");

        System.out.println("***** LLM responded: " + llmResponse);

        System.out.println("***** User says: Show me all actors in the database!");
        llmResponse = assistant.chat("Show me all actors in the database!");

        System.out.println("***** LLM responded: " + llmResponse);
    }
}