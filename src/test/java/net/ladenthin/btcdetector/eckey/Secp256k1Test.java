package net.ladenthin.bitcoinaddressfinder.eckey;

import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import static net.ladenthin.bitcoinaddressfinder.eckey.Secp256k1.getPublicKey;
import static net.ladenthin.bitcoinaddressfinder.eckey.Secp256k1.byteArrayToHexString;
import static net.ladenthin.bitcoinaddressfinder.eckey.Secp256k1.hexStringToByteArray;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

public class Secp256k1Test {

    @Test
    public void testme() throws Exception {
        System.out.println("Generate ECPublicKey from PrivateKey (String) for curve secp256k1 (final)");
        System.out.println("Check keys with https://gobittest.appspot.com/Address");
        // https://gobittest.appspot.com/Address
        String privateKey = "D12D2FACA9AD92828D89683778CB8DFCCDBD6C9E92F6AB7D6065E8AACC1FF6D6";
        String publicKeyExpected = "04661BA57FED0D115222E30FE7E9509325EE30E7E284D3641E6FB5E67368C2DB185ADA8EFC5DC43AF6BF474A41ED6237573DC4ED693D49102C42FFC88510500799";
        System.out.println("\nprivatekey given : " + privateKey);
        System.out.println("publicKeyExpected: " + publicKeyExpected);
        // routine with bouncy castle
        System.out.println("\nGenerate PublicKey from PrivateKey with BouncyCastle");
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1"); // this ec curve is used for bitcoin operations
        org.bouncycastle.math.ec.ECPoint pointQ = spec.getG().multiply(new BigInteger(1, hexStringToByteArray(privateKey)));
        byte[] publickKeyByte = pointQ.getEncoded(false);
        String publicKeyBc = byteArrayToHexString(publickKeyByte);
        System.out.println("publicKeyExpected: " + publicKeyExpected);
        System.out.println("publicKey BC     : " + publicKeyBc);
        System.out.println("publicKeys match : " + publicKeyBc.contentEquals(publicKeyExpected));

        // regeneration of ECPublicKey with java native starts here
        System.out.println("\nGenerate PublicKey from PrivateKey with Java native routines");
        // the preset "303E.." only works for elliptic curve secp256k1
        // see answer by user dave_thompson_085
        // https://stackoverflow.com/questions/48832170/generate-ec-public-key-from-byte-array-private-key-in-native-java-7
        String privateKeyFull = "303E020100301006072A8648CE3D020106052B8104000A042730250201010420"
                + privateKey;
        byte[] privateKeyFullByte = hexStringToByteArray(privateKeyFull);
        System.out.println("privateKey full  : " + privateKeyFull);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PrivateKey privateKeyNative = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyFullByte));
        ECPrivateKey ecPrivateKeyNative = (ECPrivateKey) privateKeyNative;
        ECPublicKey ecPublicKeyNative = getPublicKey(ecPrivateKeyNative);
        byte[] ecPublicKeyNativeByte = ecPublicKeyNative.getEncoded();
        String publicKeyNativeFull = byteArrayToHexString(ecPublicKeyNativeByte);
        String publicKeyNativeHeader = publicKeyNativeFull.substring(0, 46);
        String publicKeyNativeKey = publicKeyNativeFull.substring(46, 176);
        System.out.println("ecPublicKeyFull  : " + publicKeyNativeFull);
        System.out.println("ecPublicKeyHeader: " + publicKeyNativeHeader);
        System.out.println("ecPublicKeyKey   : " + publicKeyNativeKey);
        System.out.println("publicKeyExpected: " + publicKeyExpected);
        System.out.println("publicKeys match : " + publicKeyNativeKey.contentEquals(publicKeyExpected));

        //
        ECKey ecKeyPre = ECKey.fromPrivateAndPrecalculatedPublic(hexStringToByteArray(privateKey), hexStringToByteArray(publicKeyNativeKey));
        ECKey ecKeyFull = ECKey.fromPrivate(hexStringToByteArray(privateKey), false);

        byte[] ecKeyPrePubKeyHash = ecKeyPre.getPubKeyHash();
        byte[] ecKeyPrePubKey = ecKeyPre.getPubKey();
        byte[] ecKeyFullPubKeyHash = ecKeyFull.getPubKeyHash();
        byte[] ecKeyFullPubKey = ecKeyFull.getPubKey();

        System.out.println("---BLDEBUG---");
        System.out.println("ecKeyPrePubKey   : " + byteArrayToHexString(ecKeyPrePubKey));
        System.out.println("ecKeyPrePubKeyHash   : " + byteArrayToHexString(ecKeyPrePubKeyHash));
        System.out.println("ecKeyFullPubKey: " + byteArrayToHexString(ecKeyFullPubKey));
        System.out.println("ecKeyFullPubKeyHash: " + byteArrayToHexString(ecKeyFullPubKeyHash));

    }
}
