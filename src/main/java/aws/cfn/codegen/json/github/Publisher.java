package aws.cfn.codegen.json.github;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

public class Publisher implements aws.cfn.codegen.json.Publisher {
    private final static String GITHUB_RAW =
        "https://raw.githubusercontent.com/awslabs/aws-cloudformation-template-schema/master/%s";

    private final URI checkedOutDir;
    public Publisher(File checkedOutDir) {
        this.checkedOutDir = Objects.requireNonNull(checkedOutDir).toURI();
    }

    @Override
    public URI absolute(File output, String region) throws IOException {
        URI relative = checkedOutDir.relativize(output.toURI());
        return URI.create(String.format(GITHUB_RAW, relative));
    }

    @Override
    public void publish(File output, String region) throws IOException {

    }
}
