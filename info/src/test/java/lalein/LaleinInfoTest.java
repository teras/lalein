package lalein;

import com.panayotis.lalein.Lalein;
import com.panayotis.lalein.LaleinInfo;
import com.panayotis.lalein.YamlLalein;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class LaleinInfoTest {

    @Test
    void fromString() throws IOException {
        Lalein lalein = YamlLalein.fromResource("/Localizable.yaml");
        LaleinInfo info = new LaleinInfo(lalein);

        System.out.println(info);
    }

}