package com.example.langchain4jcontentretriever;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.util.HashSet;
import java.util.Set;

@Node
public class ActorNode {

    @Id
    private String name;

    @Relationship(type = "PLAYED_IN", direction = Relationship.Direction.OUTGOING)
    private Set<MovieNode> movies = new HashSet<>();

    public ActorNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<MovieNode> getMovies() {
        return movies;
    }

    public void addMovie(MovieNode movie) {
        this.movies.add(movie);
    }
}