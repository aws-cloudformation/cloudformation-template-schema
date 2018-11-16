package aws.cfn.codegen;

public class CfnSpecificationException extends RuntimeException {
    public CfnSpecificationException(String msg) {
        super(msg);
    }

    public CfnSpecificationException(String msg, Throwable chain) {
        super(msg, chain);
    }
}
