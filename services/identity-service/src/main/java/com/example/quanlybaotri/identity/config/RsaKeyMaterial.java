package com.example.quanlybaotri.identity.config;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RsaKeyMaterial {
    @Bean
    RSAKey rsaKey(@Value("${app.jwt.key-directory:/data/keys}") Path directory) throws Exception {
        Files.createDirectories(directory);
        Path privateFile = directory.resolve("jwt-private.der");
        Path publicFile = directory.resolve("jwt-public.der");
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (Files.exists(privateFile) && Files.exists(publicFile)) {
            privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privateFile)));
            publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicFile)));
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
            Files.write(privateFile, privateKey.getEncoded());
            Files.write(publicFile, publicKey.getEncoded());
        }
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("maintenance-identity-v1").build();
    }
}
