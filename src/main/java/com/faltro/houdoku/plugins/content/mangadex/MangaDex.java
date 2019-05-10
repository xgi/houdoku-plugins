package com.faltro.houdoku.plugins.content;

import com.faltro.houdoku.exception.ContentUnavailableException;
import com.faltro.houdoku.model.Chapter;
import com.faltro.houdoku.model.Languages;
import com.faltro.houdoku.model.Languages.Language;
import com.faltro.houdoku.model.Series;
import com.faltro.houdoku.util.ParseHelpers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.scene.image.Image;
import okhttp3.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static com.faltro.houdoku.net.Requests.*;

public class MangaDex extends GenericContentSource {
    public static final int ID = 0;
    public static final String NAME = "MangaDex";
    public static final String DOMAIN = "mangadex.org";
    public static final String PROTOCOL = "https";
    public static final int REVISION = 1;

    @Override
    public ArrayList<HashMap<String, Object>> search(String query) throws IOException {
        // searching is disabled for non-authenticated users, so until that's supported in the
        // client (if ever) this functionality is only used to retrieve a series by its id
        String source = "/api/manga/" + query;
        Series series = series(source, true);

        String details = String.format("%s\n★%s/10\n%s views\n%s followers",
            series.getTitle(),
            series.author,
            series.status
        );

        HashMap<String, Object> content = new HashMap<>();
        content.put("contentSourceId", ID);
        content.put("source", source);
        content.put("title", series.getTitle());
        content.put("details", details);

        ArrayList<HashMap<String, Object>> data_arr = new ArrayList<>();
        data_arr.add(content);
        return data_arr;

        // Document document = parse(GET(client,
        //         PROTOCOL + "://" + DOMAIN + "/?page=search&title=" + query));

        // ArrayList<HashMap<String, Object>> data_arr = new ArrayList<>();
        // Elements links = document.select("a[class*=manga_title]");
        // for (Element link : links) {
        //     String linkHref = link.attr("href");
        //     String source = linkHref.substring(0, linkHref.lastIndexOf("/"));
        //     String title = link.text();
        //     Element parentDiv = link.parent().parent();
        //     String coverSrc = parentDiv.selectFirst("img").attr("src");
        //     String rating = parentDiv.selectFirst("span[title*=votes]").parent()
        //             .select("span").get(2).ownText();
        //     String follows = parentDiv.selectFirst("span[title=Follows]").parent().ownText();
        //     String views = parentDiv.selectFirst("span[title=Views]").parent().ownText();

        //     String details = String.format("%s\n★%s/10\n%s views\n%s followers",
        //             title,
        //             rating,
        //             views,
        //             follows
        //     );

        //     HashMap<String, Object> content = new HashMap<>();
        //     content.put("contentSourceId", ID);
        //     content.put("source", source);
        //     content.put("coverSrc", PROTOCOL + "://" + DOMAIN + coverSrc);
        //     content.put("title", title);
        //     content.put("details", details);

        //     data_arr.add(content);
        // }
        // return data_arr;
    }

    @Override
    public ArrayList<Chapter> chapters(Series series) throws IOException {
        String id_str = series.getSource().split("/")[2];
        Response response = GET(client, PROTOCOL + "://" + DOMAIN + "/api/manga/" + id_str);
        JsonObject json_data = new JsonParser().parse(response.body().string())
                .getAsJsonObject();
        JsonObject json_chapters = json_data.get("chapter").getAsJsonObject();

        ArrayList<Chapter> chapters = new ArrayList<>();
        for (Map.Entry<String, JsonElement> chapter : json_chapters.entrySet()) {
            JsonObject json_chapter = chapter.getValue().getAsJsonObject();

            String source = "/chapter/" + chapter.getKey();
            String title = json_chapter.get("title").getAsString();
            double chapterNum = ParseHelpers.parseDouble(json_chapter.get("chapter").getAsString());
            int volumeNum = ParseHelpers.parseInt(json_chapter.get("volume").getAsString());
            Language language = Languages.get(json_chapter.get("lang_code").getAsString());
            String group = json_chapter.get("group_name").getAsString();
            LocalDateTime localDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(json_chapter.get("timestamp").getAsLong()),
                    TimeZone.getDefault().toZoneId()
            );

            HashMap<String, Object> metadata = new HashMap<>();
            metadata.put("chapterNum", chapterNum);
            metadata.put("volumeNum", volumeNum);
            metadata.put("language", language);
            metadata.put("group", group);
            metadata.put("localDateTime", localDateTime);

            chapters.add(new Chapter(series, title, source, metadata));
        }

        return chapters;
    }

    @Override
    public Series series(String source, boolean quick) throws IOException {
        System.out.println(PROTOCOL + "://" + DOMAIN + source);
        Response response = GET(client, PROTOCOL + "://" + DOMAIN + source);
        JsonObject json_data = new JsonParser().parse(response.body().string())
                .getAsJsonObject();
        JsonObject json_manga = json_data.get("manga").getAsJsonObject();

        String title = json_manga.get("title").getAsString();
        String imageSource = json_manga.get("cover_url").getAsString();
        Image cover = imageFromURL(client, PROTOCOL + "://" + DOMAIN + imageSource, ParseHelpers.COVER_MAX_WIDTH);
        Language language = Languages.get(json_manga.get("lang_name").getAsString());
        String author = json_manga.get("author").getAsString();
        String artist = json_manga.get("artist").getAsString();
        String description = json_manga.get("description").getAsString();
        int status_code = json_manga.get("status").getAsInt();
        String status = status_code == 1 ? "Releasing" : "Finished";

        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("language", language);
        metadata.put("author", author);
        metadata.put("artist", artist);
        metadata.put("status", status);
        metadata.put("description", description);

        Series series = new Series(title, source, cover, ID, metadata);
        series.setChapters(chapters(series));
        return series;
    }

    @Override
    public Image image(Chapter chapter, int page) throws IOException, ContentUnavailableException {
        Image result = null;

        if (chapter.imageUrlTemplate != null) {
            result = imageFromURL(client, String.format(chapter.imageUrlTemplate, page));
        } else {
            Response response = GET(client, PROTOCOL + "://" + DOMAIN + "/api" + chapter.getSource());
            JsonObject json_data = new JsonParser().parse(response.body().string())
                    .getAsJsonObject();

            String status = json_data.get("status").getAsString();
            if (status.equals("OK")) {
                JsonArray pages = json_data.get("page_array").getAsJsonArray();

                chapter.images = new Image[pages.size()];
                chapter.imageUrlTemplate = json_data.get("server").getAsString() +
                        json_data.get("hash").getAsString() + "/" +
                        (pages.get(page - 1).getAsString().replace("1", "%s"));

                // rerun the method, but we should now match chapter.iUT != null
                result = image(chapter, page);
            } else if (status.equals("delayed")) {
                String group_website = json_data.get("group_website").getAsString();
                throw new ContentUnavailableException(
                        "This content is not available because its release has been delayed by the " +
                                "group.\nYou may be able to read it at: " + group_website);
            }
        }

        return result;
    }
}
