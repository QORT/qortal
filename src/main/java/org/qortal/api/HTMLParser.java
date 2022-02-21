package org.qortal.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class HTMLParser {

    private static final Logger LOGGER = LogManager.getLogger(HTMLParser.class);

    private String linkPrefix;
    private byte[] data;

    public HTMLParser(String resourceId, String inPath, String prefix, boolean usePrefix, byte[] data) {
        String inPathWithoutFilename = inPath.substring(0, inPath.lastIndexOf('/'));
        this.linkPrefix = usePrefix ? String.format("%s/%s%s", prefix, resourceId, inPathWithoutFilename) : "";
        this.data = data;
    }

    public void addAdditionalHeaderTags() {
        String fileContents = new String(data);
        Document document = Jsoup.parse(fileContents);
        String baseUrl = this.linkPrefix + "/";
        Elements head = document.getElementsByTag("head");
        if (!head.isEmpty()) {
            // Add base href tag
            String baseElement = String.format("<base href=\"%s\">", baseUrl);
            head.get(0).prepend(baseElement);
        }
        String html = document.html();
        this.data = html.getBytes();
    }

    public static boolean isHtmlFile(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) {
            return true;
        }
        return false;
    }

    public byte[] getData() {
        return this.data;
    }
}
