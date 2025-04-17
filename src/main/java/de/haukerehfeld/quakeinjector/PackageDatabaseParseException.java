package de.haukerehfeld.quakeinjector;

public class PackageDatabaseParseException extends RuntimeException {
    public PackageDatabaseParseException(String message) {
        super(message);
    }

    public PackageDatabaseParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public PackageDatabaseParseException(Throwable cause) {
        super(cause);
    }
}
