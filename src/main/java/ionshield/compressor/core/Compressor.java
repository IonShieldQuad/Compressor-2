package ionshield.compressor.core;

import java.util.List;

public interface Compressor {
    List<String> compress(List<String> lines);
    List<String> decompress(List<String> lines);
    List<String> getExtraData();
}
