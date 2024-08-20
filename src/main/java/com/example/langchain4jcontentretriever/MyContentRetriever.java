package com.example.langchain4jcontentretriever;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

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
