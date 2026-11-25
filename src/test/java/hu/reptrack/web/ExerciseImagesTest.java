package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gyakorlatképek: a static/images/exercises mappa képeit induláskor felmérjük, és csak a létezőkre hivatkozunk.
 * A teszt azt is őrzi, hogy minden képfájl egy gyakorlat képkulcsához tartozzon (elgépelt fájlnév ne maradjon észrevétlen).
 */
class ExerciseImagesTest extends IntegrationTestBase {

    @Autowired ExerciseImages images;
    @Autowired JdbcTemplate jdbc;

    @Test
    void findsTheImagesInTheFolderAndOnlyThose() {
        assertThat(images.urlFor("guggolas")).isEqualTo("/images/exercises/guggolas.jpg");
        assertThat(images.urlFor("szabadteri-futas")).isEqualTo("/images/exercises/szabadteri-futas.jpg");
        assertThat(images.urlFor("nincs-ilyen-kep")).isNull();
        assertThat(images.urlFor(null)).isNull();
    }

    @Test
    void everyImageFileBelongsToAnExercise() {
        Set<String> exerciseKeys = new HashSet<>(jdbc.queryForList(
                "select image_key from exercises where image_key is not null", String.class));
        assertThat(images.keys())
                .as("a static/images/exercises mappa minden fájljának neve egy gyakorlat képkulcsa legyen")
                .isSubsetOf(exerciseKeys);
    }

    @Test
    void prefersWebpAndIgnoresOtherFilesAndUnsafeNames(@TempDir Path folder) throws IOException {
        for (String file : List.of("evezes.jpg", "evezes.webp", "uszas.png", "Rossz Nev.jpg", "leiras.txt", "kep.JPG")) {
            Files.writeString(folder.resolve(file), "x");
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver() {
            @Override
            public Resource[] getResources(String locationPattern) throws IOException {
                try (var files = Files.list(folder)) {
                    return files.map(FileSystemResource::new).toArray(Resource[]::new);
                }
            }
        };
        ExerciseImages fromFolder = new ExerciseImages(resolver);

        assertThat(fromFolder.urlFor("evezes")).isEqualTo("/images/exercises/evezes.webp");
        assertThat(fromFolder.urlFor("uszas")).isEqualTo("/images/exercises/uszas.png");
        assertThat(fromFolder.urlFor("kep")).isEqualTo("/images/exercises/kep.JPG");
        assertThat(fromFolder.keys()).containsExactlyInAnyOrder("evezes", "uszas", "kep");
    }
}
