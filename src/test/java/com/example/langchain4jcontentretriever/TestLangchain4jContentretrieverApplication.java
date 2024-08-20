package com.example.langchain4jcontentretriever;

import org.springframework.boot.SpringApplication;

public class TestLangchain4jContentretrieverApplication {

    public static void main(String[] args) {
        SpringApplication.from(Langchain4jContentRetrieverApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
