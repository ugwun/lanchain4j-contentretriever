package com.example.langchain4jcontentretriever;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Node
public class MovieNode {

    @Id
    private String title;

    private LocalDate dateCreated;

    @Relationship(type = "PLAYED_IN", direction = Relationship.Direction.INCOMING)
    private Set<ActorNode> actors = new HashSet<>();

    public MovieNode(String title, LocalDate dateCreated) {
        this.title = title;
        this.dateCreated = dateCreated;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDate dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Set<ActorNode> getActors() {
        return actors;
    }

    public void addActor(ActorNode actor) {
        this.actors.add(actor);
        actor.addMovie(this);
    }
}