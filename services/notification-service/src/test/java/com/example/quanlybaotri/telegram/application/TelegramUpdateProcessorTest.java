package com.example.quanlybaotri.telegram.application;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class TelegramUpdateProcessorTest {

    @Test
    void ignoresStartCommandsFromGroupChats() {
        TelegramLinkService links = mock(TelegramLinkService.class);
        TelegramClient client = mock(TelegramClient.class);
        TelegramUpdateProcessor processor = new TelegramUpdateProcessor(links, client);

        processor.process(
            new TelegramClient.Update(1L, "/start valid_token", -100L, "group", 123L, "tech")
        );

        verifyNoInteractions(links, client);
    }
}
