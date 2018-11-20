package aws.cfn.codegen.json;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public interface Publisher {

    /**
     * Publisher provides the absolute path that will be used for JSON schema pointer
     * references that makes it resolve correctly. The generation will use this with
     * given publisher to determine the URI to prefix for references.
     * @param output, the local region specific output file
     * @param region, which region this is done for
     * @return URI that is the json pointer prefix
     */
    URI absolute(File output, String region) throws IOException;

    /**
     * Take the local output file for give region and publishes the artifact the location
     * as needed.
     * @param output
     * @param region
     */
    void publish(File output, String region) throws IOException;
}
