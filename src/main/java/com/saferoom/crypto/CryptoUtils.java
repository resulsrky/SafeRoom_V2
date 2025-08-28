package com.saferoom.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/*Public Key her zaman Server  üretir ve Client a gönderir 
 * 1.adım -Server RSA Keypair üretir--public key ve private key oluşturulur
 * 2.adım Server Public Key i Client a gönderir --İlk bağlantıda hemen gönderilir
 * 3.adım Client Public Key i alır ve Güven kontrolü yapar(Key Pinning) --Gömülü Fingerprint ile karşılaştırır
 * 4.adım Client AES Key üretir -Rastgele AES-256 Secret Key oluşturulur
 * 5.adım Client Key'i Server'ın Public Key'i şifreler-RSA ile şifreleme yapar
 * 6.adım Client bu şifreli AES Key'i Server'a gönderir-Artık AES Key transfer edildi
 * 7.adım Server Private Key ile AES'i çözer-Sadece Server çözebilir.
 * 8.adım Artık tüm mesajlar AES ile şifrelenir.-Session Key = AES Key*/

public class CryptoUtils {
	
	static final SecureRandom RNG = new SecureRandom();
	
	public static KeyPair x25519KeyPair() throws Exception{
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
		kpg.initialize(new NamedParameterSpec("X25519"));
		
		return kpg.generateKeyPair();
	}
	
	public static byte[] x25519Public(KeyPair kp) {
		return kp.getPublic().getEncoded();
	}
	
	public static byte[] x25519PublicRaw(KeyPair kp) throws Exception {
		    // Raw 32B public (RFC 7748)
		    return kp.getPublic().getEncoded();
		  }
	
	

	public static KeyPair generatorRSAkeyPair() throws Exception {
		KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
		keygen.initialize(3072);
		
		return keygen.generateKeyPair();
	
	}
	
	public static SecretKey generatorAESkey() throws Exception{
		KeyGenerator aesKey = KeyGenerator.getInstance("AES");
		aesKey.init(256);
		
		SecretKey secret_aes =  aesKey.generateKey();
		return secret_aes;
	}

	public static String encrypt_AESkey(SecretKey aes, PublicKey rsa_pub) throws Exception {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, rsa_pub);
	
		byte[] raw_key_data = aes.getEncoded();
		byte[] encrypt_raw_key = cipher.doFinal(raw_key_data);
		
		return   Base64.getEncoder().encodeToString(encrypt_raw_key); 
		
	}

	public static SecretKey decrypt_AESkey(String encryptedKey, PrivateKey rsa) throws Exception {
		byte[] raw_encrypted_aes_key = Base64.getDecoder().decode(encryptedKey);
		
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.DECRYPT_MODE, rsa);
		
		byte[] raw_decrypted_aes_key = cipher.doFinal(raw_encrypted_aes_key);
		
		SecretKeySpec aes_key =  new SecretKeySpec(raw_decrypted_aes_key, "AES");
		
		
		return aes_key;
	}
	
	public static byte[] encrypte_data_with_AES(String data, SecretKey aes_key) throws Exception{
		
		byte[] raw_data = data.getBytes(StandardCharsets.UTF_8);
		
		byte[] iv = new byte[16];
		SecureRandom random = new SecureRandom();
		random.nextBytes(iv);
		IvParameterSpec iv_Spec = new IvParameterSpec(iv);
		
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, aes_key, iv_Spec);
		
		byte[] encrypt_data = cipher.doFinal(raw_data);
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(iv);
		outputStream.write(encrypt_data);
		
		return outputStream.toByteArray();
	}

	public static String decrypte_data_with_AES(byte[] data,SecretKey aes_key) throws Exception {
		
		byte[] decoded_data = Base64.getDecoder().decode(data);
		byte[] iv_part = new byte[16];
		byte[] raw_data = new byte[data.length - 16];
		
		for(int i = 0; i < iv_part.length; i++) {
			iv_part[i] = decoded_data[i];//HAM VERİ IV'IN KENDİSİ
		}
		for(int j = 0; j < raw_data.length; j++) {
			raw_data[j] = decoded_data[j + 16]; //IV sonrası gelen veriyi al 
		}
		IvParameterSpec IvParameter = new IvParameterSpec(iv_part);//Kriptografik olarak bir "IV" nesnesi olarak tanımlama
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, aes_key, IvParameter);//3.PARAMETRE OLARAK IvParameterSpec ister byte array değil
		
		  byte[] decrypted_data = cipher.doFinal(raw_data);//SADECE RAW DATA KULLANILMALI IV KISMI DAHİL EDİLMEMELİ
		 
		  String string_decrypted_data = new String(decrypted_data, StandardCharsets.UTF_8);
		  
		  return string_decrypted_data;
	}
	//Encoding = Karakterleri bayta çevirme
	//Decoding = Baytları karakterlere çevirme
	//Hangi encoding kullanarak byte array e çevireceğimiz kesin olarak belirtilmeli kriptografik uygulamalar ,fonksiyonlar belirsizlik kabul etmez
	//İnsan tarafından okunabilmesi gereken(loglama) hash bu yüzden hexadecimal e çevrilmesi önemli
	public static String HashPassword(String password) throws Exception {
		
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] bytes_of_password = password.getBytes(StandardCharsets.UTF_8);//Her ortamda tutarlı olması için StandardCharsets.UTF_8 
		byte[] hash = digest.digest(bytes_of_password);
		
		StringBuilder Hexform = new StringBuilder();
		
		for(byte i : hash) {
			Hexform.append(String.format("%02x", i));
		}
		
		return Hexform.toString();
	}
	
	public static String generateSalt() {
		
		byte[] salt = new byte[16];
		SecureRandom random = new SecureRandom();
		random.nextBytes(salt);
		
		String urlFriendlySalt = Base64.getEncoder().encodeToString(salt);
		
		return urlFriendlySalt;
	}
	//Veri tabanına veya dosyaya veri kaydetmeye yarayan fonksiyon(insan tarafından okunabilmesi önemli değil)
	
	
	public static String hashPasswordWithSalt(String password, String salt) throws Exception {
				
		byte[] saltBytes = Base64.getDecoder().decode(salt);
		byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		
		outputStream.write(saltBytes);//İlk salt ekleniyor bunu verify işlemlerinde unutma 
		outputStream.write(passwordBytes);
		
		
		byte[] salted_password = outputStream.toByteArray();
				
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		
		byte[] hash = digest.digest(salted_password);
		
		
		return Base64.getEncoder().encodeToString(hash);	
	}
	
	
	public static boolean constantTimeEquals(String a, String b) {
	    if (a.length() != b.length()) return false;

	    int result = 0;
	    for (int i = 0; i < a.length(); i++) {
	        result |= a.charAt(i) ^ b.charAt(i);
	    }
	    return result == 0;
	}

}