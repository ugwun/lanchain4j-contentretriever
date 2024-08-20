package com.example.langchain4jcontentretriever;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActorRepository extends Neo4jRepository<ActorNode, String> {

    @Query("MATCH (a:ActorNode)-[:PLAYED_IN]->(m:MovieNode {title: $movieTitle}) RETURN a")
    List<ActorNode> findActorsByMovieTitle(@Param("movieTitle") String movieTitle);
}