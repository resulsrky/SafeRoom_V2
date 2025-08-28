package com.saferoom.crypto;

import java.security.KeyPair;

import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.SecretKey;


public class KeyExchange {
	
	  public static KeyPair keypair;
	  public static PublicKey publicKey;
	  public static PrivateKey privateKey;
	  public static SecretKey AES_Key;
	  
	    public static void init() {
	        try {
	            KeyPair keyPair = CryptoUtils.generatorRSAkeyPair();
				keypair = keyPair;
	            publicKey = keyPair.getPublic();
	            privateKey = keyPair.getPrivate();
	            System.out.println("[KEYSTORE] RSA KeyPair oluşturuldu.");
	            System.out.println("[KEYSTORE] Public Key (Base64): " + java.util.Base64.getEncoder().encodeToString(publicKey.getEncoded()));
	        } catch (Exception e) {
	            System.err.println("[KEYSTORE] RSA anahtarları oluşturulamadı: " + e.getMessage());
	        }
	    }
	    public static void create_AES() {
	    	try {
	    		SecretKey aes_key = CryptoUtils.generatorAESkey();
	    		AES_Key = aes_key;
	    		System.out.println("[KEYSTORE] AES Key (Base64): " + java.util.Base64.getEncoder().encodeToString(AES_Key.getEncoded()));
	    		
	    	}
	    	catch(Exception e) {
	    		System.err.println("[KRYSTORE| AES anahtarı oluşturulamadı: " + e.getMessage());
	    	}
	    	
	    }
	    

	

}
