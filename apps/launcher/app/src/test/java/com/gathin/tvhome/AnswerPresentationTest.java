package com.gathin.tvhome;

import com.gathin.tvhome.feature.agent.coordinator.AnswerPresentation;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerPresentationTest {
    @Test
    public void proseDoesNotStartBrowser() {
        assertFalse(AnswerPresentation.needsBrowser("Máy chủ còn 72 GB.\nĐang hoạt động bình thường."));
    }

    @Test
    public void markdownElementsStartBrowser() {
        assertTrue(AnswerPresentation.needsBrowser("## Tiêu đề tin tức"));
        assertTrue(AnswerPresentation.needsBrowser("1. Google công bố tính năng mới"));
        assertTrue(AnswerPresentation.needsBrowser("- Điểm tin số 1"));
        assertTrue(AnswerPresentation.needsBrowser("```\ncode block\n```"));
    }

    @Test
    public void cardsAndImagesKeepBrowser() {
        assertTrue(AnswerPresentation.needsBrowser("<div class=card>Phim</div>"));
        assertTrue(AnswerPresentation.needsBrowser("![poster](https://example.com/a.jpg)"));
        assertTrue(AnswerPresentation.needsBrowser("![poster](https://example.com/a.jpg)<img src=x>"));
    }

    @Test
    public void tablesKeepBrowser() {
        assertTrue(AnswerPresentation.needsBrowser("| Tên | Trạng thái |\n| TV | Bật |"));
    }

    @Test
    public void formattingRetainsWordsAndLinkTargets() {
        assertEquals("Xin chào\nĐang bật\nWeb (https://example.com)",
                AnswerPresentation.plainText(
                        "## Xin chào\n**Đang bật**\n[Web](https://example.com)"));
    }
}
