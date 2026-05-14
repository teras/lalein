package lalein;

import com.panayotis.lalein.Lalein;
import com.panayotis.lalein.LaleinInfo;
import com.panayotis.lalein.ParameterInfo;
import com.panayotis.lalein.TranslationInfo;
import com.panayotis.lalein.YamlLalein;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LaleinInfoTest {

    @Test
    void infoReflectsLaleinStructure() throws IOException {
        Lalein lalein = YamlLalein.fromResource("/Localizable.yaml");
        LaleinInfo info = new LaleinInfo(lalein);

        List<TranslationInfo> translations = new ArrayList<>();
        info.getTranslations().forEach(translations::add);

        // 3 entries: peaches, apples, baskets_with_oranges
        assertEquals(3, translations.size());

        TranslationInfo peaches = findByHandler(translations, "peaches");
        assertNotNull(peaches);
        assertEquals("I have peaches.", peaches.getFormat());
        assertEquals(0, peaches.getParameterCount());

        TranslationInfo apples = findByHandler(translations, "apples");
        assertNotNull(apples);
        assertEquals(1, apples.getParameterCount());

        TranslationInfo basket = findByHandler(translations, "baskets_with_oranges");
        assertNotNull(basket);
        // baskets + oranges + oranges_zero_basket = 3 parameters
        assertEquals(3, basket.getParameterCount());
    }

    @Test
    void parameterInfoExposesPluralForms() throws IOException {
        Lalein lalein = YamlLalein.fromResource("/Localizable.yaml");
        LaleinInfo info = new LaleinInfo(lalein);

        TranslationInfo apples = findByHandler(toList(info), "apples");
        ParameterInfo mainParam = toList(apples.getParameters()).get(0);

        assertEquals("I don't have apples.", mainParam.getZero());
        assertEquals("I have an apple.", mainParam.getOne());
        assertEquals("I have two apples.", mainParam.getTwo());
        assertEquals("I have %d apples.", mainParam.getOther());
        assertNull(mainParam.getFew());
        assertNull(mainParam.getMany());
        assertSame(apples, mainParam.getParent());
    }

    @Test
    void toStringSummary() throws IOException {
        Lalein lalein = YamlLalein.fromResource("/Localizable.yaml");
        LaleinInfo info = new LaleinInfo(lalein);
        assertEquals("Lalein[3]", info.toString());
    }

    private static TranslationInfo findByHandler(List<TranslationInfo> all, String handler) {
        return all.stream().filter(t -> handler.equals(t.getHandler())).findFirst().orElse(null);
    }

    private static <T> List<T> toList(Iterable<T> it) {
        List<T> out = new ArrayList<>();
        it.forEach(out::add);
        return out;
    }

    private static List<TranslationInfo> toList(LaleinInfo info) {
        return toList(info.getTranslations());
    }
}
