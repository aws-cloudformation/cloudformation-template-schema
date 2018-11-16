package aws.cfn.codegen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * This loads a specification file into memory. URL could point to remote site or local
 * file. It uses {@link ObjectMapper} to get the underlying model
 */
public class SpecificationLoader {

    private final ObjectMapper mapperForJSON;
    private final MappingJsonFactory jsonFactory;
    public SpecificationLoader() {
        jsonFactory = new MappingJsonFactory();
        mapperForJSON = new ObjectMapper(jsonFactory);
        mapperForJSON.configure(MapperFeature.USE_STD_BEAN_NAMING, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            .configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, true)
            .configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        jsonFactory.setCodec(mapperForJSON);
        mapperForJSON.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
    }

    public CfnSpecification loadSpecification(URL location) throws IOException {
        try (PushbackInputStream stream = new PushbackInputStream(location.openStream(), 8)) {
            byte[] magic = new byte[4];
            int nread = stream.read(magic);
            if (nread < 4) {
                throw new IOException("Can not read stream");
            }
            int magicNum = ((int)magic[0] & 0xFF) | ((int)magic[1] & 0xFF) << 8;
            Reader reader;
            if (GZIPInputStream.GZIP_MAGIC == magicNum) {
                stream.unread(magic);
                reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8);
            }
            else {
                magicNum |= ((int)magic[2] & 0xFF) << 16 | ((int)magic[3] & 0xFF) << 24;
                if (magicNum == 0x04034b50) {
                    stream.unread(magic);
                    reader = new InputStreamReader(new ZipInputStream(stream), StandardCharsets.UTF_8);
                }
                else {
                    stream.unread(magic);
                    reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                }
            }

            return mapperForJSON.readValue(reader, CfnSpecification.class);
        }

    }

    public SingleCfnSpecification loadSingleResourceSpecification(URL location) throws IOException {
        try (PushbackInputStream stream = new PushbackInputStream(location.openStream(), 8)) {
            byte[] magic = new byte[4];
            int nread = stream.read(magic);
            if (nread < 4) {
                throw new IOException("Can not read stream");
            }
            int magicNum =  0x01 << (int)magic[3] | 0x01 << (int)magic[2] |
                            0x01 << (int)magic[2] | 0x01 << (int)magic[2];
            Reader reader;
            if (GZIPInputStream.GZIP_MAGIC == magicNum) {
                stream.unread(magic);
                reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8);
            }
            else if (magicNum == 0x0403) {
                stream.unread(magic);
                reader = new InputStreamReader(new ZipInputStream(stream), StandardCharsets.UTF_8);
            }
            else {
                stream.unread(magic);
                reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            }
            return mapperForJSON.readValue(reader, SingleCfnSpecification.class);
        }
    }
}
