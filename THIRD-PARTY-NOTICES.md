# Third-party notices

The packaged Lambda is a shaded JAR and includes third-party libraries. This file lists the
direct dependencies and the most relevant transitive components. It is informational, not legal
advice. The original license and notice files are included in the shaded JAR under `META-INF/`
where the upstream artifacts provide them.

| Component | Version | License | Project |
|---|---:|---|---|
| Digital Signature Service (DSS) | 6.5.1 | LGPL-2.1 | https://github.com/esig/dss |
| Bouncy Castle (bcprov, bcpkix, bcutil) | 1.86 | MIT | https://www.bouncycastle.org/ |
| AWS SDK for Java v2 (Secrets Manager, URL connection client) | 2.57.0 | Apache-2.0 | https://aws.amazon.com/sdk-for-java/ |
| AWS Lambda Java Core | 1.4.1 | Apache-2.0 | https://github.com/aws/aws-lambda-java-libs |
| AWS Lambda Java Events | 3.16.1 | Apache-2.0 | https://github.com/aws/aws-lambda-java-libs |
| Jackson Databind | 2.22.3 | Apache-2.0 | https://github.com/FasterXML/jackson |
| Apache Santuario / XML Security | 3.0.6 | Apache-2.0 | https://santuario.apache.org/ |
| SLF4J API / NOP | 2.0.20 | MIT | https://www.slf4j.org/ |
| JUnit Jupiter | 5.11.4 | EPL-2.0 | https://junit.org/junit5/ |

## LGPL note

DSS is licensed under LGPL-2.1. This project uses DSS as a library and does not modify DSS
source code. If you redistribute a modified version of DSS, review the LGPL-2.1 obligations and
include the corresponding source. The unmodified DSS source is available at the project URL
above.

## SRI documents

The SRI technical specification and XML schemas are published by the Servicio de Rentas Internas
of Ecuador. They are not included in this repository. Their use is subject to the terms of the
publishing entity.
