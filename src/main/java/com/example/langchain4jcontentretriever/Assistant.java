package com.example.langchain4jcontentretriever;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface Assistant {

    @SystemMessage({"You're a helpful Expert."})
    String chat(@UserMessage String userMessage);
}
