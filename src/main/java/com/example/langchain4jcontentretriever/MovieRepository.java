package com.example.langchain4jcontentretriever;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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