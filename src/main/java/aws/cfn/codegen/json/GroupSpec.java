package aws.cfn.codegen.json;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * GroupSpec allows for generating the schema for only a subset of resources grouped together
 * for a specific purpose. See the included resource file config.yml for a set of default groupings
 * like networking (for VPC, Subnet and related stuff), serverless, for Lambda, ApiGateway and IAM
 * etc.
 */
@lombok.EqualsAndHashCode
@lombok.ToString
public class GroupSpec {

    public static GroupSpec includesOnly(String name, String... includes) {
        return new GroupSpec(name, Sets.newHashSet(includes), null);
    }

    public static GroupSpec excludesOnly(String name, String... excludes) {
        return new GroupSpec(name, null, Sets.newHashSet(excludes));
    }

    @lombok.Setter
    @lombok.Getter
    private Set<String> includes;
    @lombok.Setter
    @lombok.Getter
    private Set<String> excludes;
    @lombok.Setter
    @lombok.Getter
    private String groupName;
    private List<Pattern> incPatterns = new ArrayList<>(5);
    private List<Pattern> exPatterns = new ArrayList<>(5);

    public GroupSpec() {}
    public GroupSpec(String name, Set<String> includes, Set<String> excludes) {
        groupName = Objects.requireNonNull(name);
        this.includes = includes;
        this.excludes = excludes;
    }

    public void compile() {
        if (includes != null) {
            for (String each: includes) {
                incPatterns.add(Pattern.compile(each));
            }
        }

        if (excludes != null) {
            for (String each: excludes) {
                exPatterns.add(Pattern.compile(each));
            }
        }
    }

    public boolean isIncluded(String resourceType) {
        boolean included = false;
        for (Iterator<Pattern> each = incPatterns.iterator();
             !included && each.hasNext();) {
            // Matcher is never null
            included = each.next().matcher(resourceType).matches();
        }

        boolean excluded = false;
        for (Iterator<Pattern> each = exPatterns.iterator();
             !excluded && each.hasNext(); ) {
            // Matcher is never null
            excluded = each.next().matcher(resourceType).matches();
        }

        return included && !excluded;
    }

}
