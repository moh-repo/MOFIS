package com.dilaw.mohe.notification.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/**
 * The message body as plain text, for the channels that cannot show HTML (SMS,
 * WhatsApp). The send screen composes e-mail in a rich-text editor, so a message
 * sent on e-mail and SMS together arrives here as {@code <p>...</p>}; without this
 * the phone showed the tags.
 */
final class MessageText {

    private MessageText() {
    }

    static String plain(String body) {
        if (body == null) return "";
        if (body.indexOf('<') < 0) return body.trim();
        Document doc = Jsoup.parse(body);
        // wholeText() already renders <br> as a line break; blocks need one added.
        for (Element li : doc.select("li")) {
            li.prependChild(new TextNode("- "));
        }
        for (Element block : doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, blockquote")) {
            block.after(new TextNode("\n"));
        }
        return doc.body().wholeText()
                .replace(' ', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
