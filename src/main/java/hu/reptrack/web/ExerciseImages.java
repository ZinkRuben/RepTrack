package hu.reptrack.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * A gyakorlatképek a {@code src/main/resources/static/images/exercises} mappában vannak, a nevük a gyakorlat
 * képkulcsa ({@code exercises.image_key}) és a kiterjesztés (.webp, .jpg, .jpeg vagy .png). Induláskor felmérjük,
 * milyen képek vannak, és a gyakorlatválasztó csak a létezőkre hivatkozik; kép nélkül a kategória ikonja látszik.
 * Új kép felvételéhez elég a fájlt a mappába tenni a megfelelő néven, és újraépíteni az alkalmazást.
 */
@Component
public class ExerciseImages {

    static final String FOLDER = "static/images/exercises/";
    private static final String URL_PREFIX = "/images/exercises/";
    private static final List<String> EXTENSIONS = List.of("webp", "jpg", "jpeg", "png");   // előnyben az előbbi
    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Logger log = LoggerFactory.getLogger(ExerciseImages.class);

    private final Map<String, String> urls = new HashMap<>();

    public ExerciseImages() {
        this(new PathMatchingResourcePatternResolver());
    }

    ExerciseImages(ResourcePatternResolver resolver) {
        Map<String, Integer> rank = new HashMap<>();
        try {
            for (Resource resource : resolver.getResources("classpath*:" + FOLDER + "*")) {
                String file = resource.getFilename();
                int dot = file == null ? -1 : file.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String key = file.substring(0, dot);
                int extension = EXTENSIONS.indexOf(file.substring(dot + 1).toLowerCase());
                if (extension < 0 || !KEY.matcher(key).matches()) {
                    continue;
                }
                if (!rank.containsKey(key) || extension < rank.get(key)) {
                    rank.put(key, extension);
                    urls.put(key, URL_PREFIX + file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("A gyakorlatképek mappája nem olvasható: " + FOLDER, e);
        }
        log.info("Gyakorlatképek: {} kép a {} mappában.", urls.size(), FOLDER);
    }

    /** A kép címe a böngésző számára (pl. /images/exercises/guggolas.jpg), vagy null, ha nincs ilyen kép. */
    public String urlFor(String imageKey) {
        return imageKey == null ? null : urls.get(imageKey);
    }

    /** A talált képek kulcsai (a tesztek ellenőrzik, hogy mind egy gyakorlathoz tartozik-e). */
    java.util.Set<String> keys() {
        return java.util.Set.copyOf(urls.keySet());
    }
}
