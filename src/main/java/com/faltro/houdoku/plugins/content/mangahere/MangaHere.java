package com.faltro.houdoku.plugins.content;

import com.faltro.houdoku.exception.ContentUnavailableException;
import com.faltro.houdoku.exception.LicensedContentException;
import com.faltro.houdoku.model.Chapter;
import com.faltro.houdoku.model.Series;
import com.faltro.houdoku.util.ParseHelpers;
import javafx.scene.image.Image;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import okhttp3.Response;
import javax.script.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static com.faltro.houdoku.net.Requests.*;

public class MangaHere extends GenericContentSource {
    public static final int ID = 1;
    public static final String NAME = "MangaHere";
    public static final String DOMAIN = "www.mangahere.cc";
    public static final String PROTOCOL = "http";
    public static final int REVISION = 1;

    @Override
    public ArrayList<HashMap<String, Object>> search(String query) throws IOException {
        Document document = parse(GET(client,
                PROTOCOL + "://" + DOMAIN + "/search?title=" + query));

        ArrayList<HashMap<String, Object>> data_arr = new ArrayList<>();
        Elements results = document.selectFirst("ul[class=manga-list-4-list line]").select("li");
        for (Element result : results) {
            Element link = result.selectFirst("a");
            String source = link.attr("href");
            String cover_src = link.select("img").attr("src");
            String title = link.attr("title");
            String status = result.select("p").get(1).selectFirst("a").ownText();
            Element author_container = result.select("p").get(2).selectFirst("a");
            String author = author_container == null ? "???" : author_container.attr("title");
            Element latest_container = result.select("p").get(3).selectFirst("a");
            String latest = latest_container == null ? "???" : latest_container.ownText();

            String details = String.format("%s\nAuthor: %s\nStatus: %s\nLatest: %s",
                    title,
                    author,
                    status,
                    latest
            );

            HashMap<String, Object> content = new HashMap<>();
            content.put("contentSourceId", ID);
            content.put("source", source);
            content.put("coverSrc", cover_src);
            content.put("title", title);
            content.put("details", details);

            data_arr.add(content);
        }
        return data_arr;
    }

    @Override
    public ArrayList<Chapter> chapters(Series series, Document seriesDocument) throws
            ContentUnavailableException {
        if (seriesDocument.selectFirst("p[class=detail-block-content]") != null) {
            throw new ContentUnavailableException("This content is restricted and could not be " +
                    "accessed on " + NAME + ".");
        }

        Elements rows = seriesDocument.selectFirst("ul[class=detail-main-list]").select("li");

        ArrayList<Chapter> chapters = new ArrayList<>();
        for (Element row : rows) {
            Element link = row.selectFirst("a");
            String title = link.attr("title");
            String source_extended = link.attr("href");
            String source = source_extended.substring(0, source_extended.lastIndexOf("/") + 1);
            double chapterNum = ParseHelpers.parseDouble(
                    title.substring(title.indexOf("Ch.") + 3)
            );
            String dateString = link.selectFirst("p[class=title2]").text();
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
                    "MMM d,yyyy", Locale.ENGLISH
            );
            LocalDateTime localDateTime = ParseHelpers.potentiallyRelativeDate(
                    dateString, dateTimeFormatter);

            HashMap<String, Object> metadata = new HashMap<>();
            metadata.put("chapterNum", chapterNum);
            metadata.put("localDateTime", localDateTime);

            chapters.add(new Chapter(series, title, source, metadata));
        }

        return chapters;
    }

    @Override
    public Series series(String source, boolean quick) throws IOException,
            ContentUnavailableException {
        Document seriesDocument = parse(GET(client, PROTOCOL + "://" + DOMAIN + source));

        Element detail_container = seriesDocument.selectFirst("div[class=detail-info]");
        String image_source = detail_container.selectFirst("img").attr("src");
        String title = detail_container.selectFirst("img").attr("alt");
        Image cover = imageFromURL(client, image_source, ParseHelpers.COVER_MAX_WIDTH);

        Element right_container = detail_container.selectFirst("div[class=detail-info-right]");
        String status = right_container.select("p").get(0).select("span").get(1).ownText();
        String author = right_container.select("p").get(1).selectFirst("a").ownText();
        String[] genres = ParseHelpers.htmlListToStringArray(
                right_container.select("p").get(2), "a");
        String description = right_container.selectFirst("p[class=fullcontent]").ownText();

        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("author", author);
        metadata.put("artist", author);
        metadata.put("altNames", new String[]{});
        metadata.put("status", status);
        metadata.put("description", description);
        metadata.put("genres", genres);

        Series series = new Series(title, source, cover, ID, metadata);
        series.setChapters(chapters(series, seriesDocument));
        return series;
    }

    @Override
    public Image cover(String source) throws IOException {
        Document seriesDocument = parse(GET(client, PROTOCOL + "://" + DOMAIN + source));
        Image result = null;
        Element coverElement = seriesDocument.selectFirst("img[class=img]");
        if (coverElement != null) {
            result = imageFromURL(client, coverElement.attr("src"), ParseHelpers.COVER_MAX_WIDTH);
        }
        return result;
    }

    @Override
    public Image image(Chapter chapter, int page) throws IOException, ContentUnavailableException {
        String url = PROTOCOL + "://" + DOMAIN + chapter.getSource() + Integer.toString(page) + ".html";
        Document document = parse(GET(client, url));

        Elements errors = document.select("div[class=mangaread_error]");
        if (errors.size() > 0) {
            if (errors.first().text().contains("has been licensed")) {
                throw new LicensedContentException("This content has been licensed and is not " +
                        "available on " + NAME + ".");
            }
        }

        // evaluate javascript function to get key for script url
        String html = document.toString();
        int idxScript1 = html.indexOf("eval(function(p,a,c,k,e,d)");
        String script_extended = html.substring(idxScript1);
        int idxScript2 = script_extended.indexOf("</script>");
        String script = html.substring(idxScript1, idxScript1 + idxScript2 - 2).substring(5);

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("javascript");
        
        String key = "";
        try {
            engine.eval("var pre = (" + script + ");");
            String pre = (String) engine.get("pre");

            int idxKey1 = pre.indexOf("'");
            int idxKey2 = pre.indexOf(";");
            
            script = pre.substring(idxKey1, idxKey2);
            engine.eval("var key = (" + script + ");");
            key = (String) engine.get("key");
        } catch (ScriptException e) {
            return null;
        }

        if (key == "") return null;

        // use key to download another script
        int idxChapterid1 = html.indexOf("chapterid =");
        String chapterid_extended = html.substring(idxChapterid1);
        int idxChapterid2 = chapterid_extended.indexOf(";");
        String chapterid = html.substring(idxChapterid1 + 11, idxChapterid1 + idxChapterid2);

        String script_url = url + String.format("/chapterfun.ashx?cid=%s&page=%d&key=%s",
            chapterid, page, key
        );
        System.out.println(script_url);
        Response response = GET(client, script_url);
        script = response.body().string();
        script = script.substring(5, script.length() - 2);

        String deobfuscated = "";
        try {
            engine.eval("var deobfuscated = (" + script + ");");
            deobfuscated = (String) engine.get("deobfuscated");
        } catch (ScriptException e) {
            return null;
        }

        if (deobfuscated == "") return null;

        // extract image url from deobfuscated script
        int idxBase1 = deobfuscated.indexOf("pix=\"");
        String base_extended = deobfuscated.substring(idxBase1);
        int idxBase2 = base_extended.indexOf(";");
        String base = deobfuscated.substring(idxBase1, idxBase1 + idxBase2);
        System.out.println(base);

        // we may not have determined the number of pages yet, so do that here
        if (chapter.images.length == 1) {
            Elements page_links = document.select("div[class=pager-list-left]").last().select("a");
            Element last_link = page_links.get(page_links.size() - 3);
            int num_pages = ParseHelpers.parseInt(last_link.ownText());

            chapter.images = new Image[num_pages];
        }

        // return imageFromURL(client, url);
        return null;
    }
}
