package com.julioserpone.srixadessigner.cli;

import com.julioserpone.srixadessigner.XadesBesSigner;
import com.julioserpone.srixadessigner.config.SignerConfig;
import com.julioserpone.srixadessigner.crypto.CertificateLoader;
import com.julioserpone.srixadessigner.crypto.SigningIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Local CLI for testing the signer without AWS. */
public final class SignCli {

  private SignCli() {}

  public static void main(String[] args) {
    try {
      run(args);
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }

  static void run(String[] args) throws Exception {
    Map<String, String> options = parse(args);
    if (options.containsKey("help")) {
      printUsage();
      return;
    }
    if (options.containsKey("version")) {
      System.out.println("sri-xades-signer");
      return;
    }

    Path input = Path.of(required(options, "in"));
    Path output = Path.of(required(options, "out"));
    Path pkcs12 = Path.of(required(options, "p12"));
    String alias = options.get("alias");
    char[] password = resolvePassword(options);

    try {
      SigningIdentity identity =
          CertificateLoader.load(Files.readAllBytes(pkcs12), password, alias);
      byte[] signed =
          new XadesBesSigner(SignerConfig.fromEnvironment())
              .sign(Files.readAllBytes(input), identity);
      Files.write(output, signed);
      System.out.println("Firmado: " + output);
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  private static char[] resolvePassword(Map<String, String> options) {
    String passwordEnv = options.get("password-env");
    if (passwordEnv != null && !passwordEnv.isBlank()) {
      String value = System.getenv(passwordEnv);
      if (value == null) {
        throw new IllegalArgumentException(
            "The environment variable is not defined: " + passwordEnv);
      }
      return value.toCharArray();
    }
    String password = options.get("password");
    if (password == null) {
      throw new IllegalArgumentException("Use --password-env or --password");
    }
    return password.toCharArray();
  }

  private static String required(Map<String, String> options, String key) {
    String value = options.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required option --" + key);
    }
    return value;
  }

  private static Map<String, String> parse(String[] args) {
    Map<String, String> options = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (!arg.startsWith("--")) {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
      String key = arg.substring(2);
      String value;
      int equals = key.indexOf('=');
      if (equals >= 0) {
        value = key.substring(equals + 1);
        key = key.substring(0, equals);
      } else if ("help".equals(key) || "version".equals(key)) {
        value = "true";
      } else {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value for --" + key);
        }
        value = args[++i];
      }
      options.put(key, value);
    }
    return options;
  }

  private static void printUsage() {
    System.out.println(
        """
        Uso:
          java -jar sri-xades-signer.jar \\
            --in comprobante.xml \\
            --out comprobante-firmado.xml \\
            --p12 certificado.p12 \\
            --password-env P12_PASSWORD \\
            [--alias alias]

        Opciones:
          --password-env VAR   Lee la contraseña de una variable de entorno.
          --password VALOR     Contraseña directa; solo para pruebas locales.
          --alias ALIAS        Alias de la clave privada en el PKCS#12.
          --help               Muestra esta ayuda.
        """);
  }
}
