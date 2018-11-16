package aws.cfn.codegen;

import aws.cfn.codegen.json.GroupSpec;
import com.google.common.collect.Sets;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GroupSpecTest {

    private final List<String> resources;

    public GroupSpecTest() throws IOException {
        resources = Files.readAllLines(
            Paths.get("src/test/java/aws/cfn/codegen/resources.txt"));
    }

    @Test
    public void testPatterns() {
        Pattern pattern = Pattern.compile("AWS::EC2.*");
        assertTrue(pattern.matcher("AWS::EC2::VPC").matches());

        pattern = Pattern.compile("AWS::EC2::Host");
        assertFalse(pattern.matcher("AWS::EC2::VPC").matches());
        assertTrue(pattern.matcher("AWS::EC2::Host").matches());

        String source = "AWS::EC2::(Spot|Launch|Instance|Volume|Host).*";
        pattern = Pattern.compile(source);
        assertTrue(pattern.matcher("AWS::EC2::LaunchTemplate").matches());
    }


    @Test
    public void testSpec() throws IOException {
        GroupSpec spec = GroupSpec.includesOnly("ec2","AWS::EC2.*");
        spec.compile();
        List<String> ec2 = resources.stream()
            .filter(spec::isIncluded)
            .collect(Collectors.toList());
        assertEquals(95, ec2.size());
    }

    @Test
    public void testSpecNetworking() throws IOException {
        GroupSpec spec = new GroupSpec();
        spec.setIncludes(Sets.newHashSet("AWS::EC2.*"));
        spec.setExcludes(Sets.newHashSet("AWS::EC2::(Spot|Launch|Instance|Volume|Host).*"));
        spec.setGroupName("networking");
        spec.compile();
        List<String> networking = resources.stream()
            .filter(spec::isIncluded)
            .collect(Collectors.toList());
        assertEquals(43, networking.size());
    }
}
