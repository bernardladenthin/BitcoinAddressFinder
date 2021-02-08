package net.ladenthin.bitcoinaddressfinder.eckey;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;

// https://stackoverflow.com/questions/19673962/codes-to-generate-a-public-key-in-an-elliptic-curve-algorithm-using-a-given-priv/42797410#42797410
public class Secp256k1 {

    final static BigInteger FieldP_2 = BigInteger.valueOf(2); // constant for scalar operations
    final static BigInteger FieldP_3 = BigInteger.valueOf(3); // constant for scalar operations

    public static String byteArrayToHexString(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    // scalar operations for native java
    // see https://stackoverflow.com/a/42797410/8166854
    // written by author: SkateScout
    private static ECPoint doublePoint(final BigInteger p, final BigInteger a, final ECPoint R) {
        if (R.equals(ECPoint.POINT_INFINITY)) {
            return R;
        }
        BigInteger slope = (R.getAffineX().pow(2)).multiply(FieldP_3);
        slope = slope.add(a);
        slope = slope.multiply((R.getAffineY().multiply(FieldP_2)).modInverse(p));
        final BigInteger Xout = slope.pow(2).subtract(R.getAffineX().multiply(FieldP_2)).mod(p);
        final BigInteger Yout = (R.getAffineY().negate()).add(slope.multiply(R.getAffineX().subtract(Xout))).mod(p);
        return new ECPoint(Xout, Yout);
    }

    private static ECPoint addPoint(final BigInteger p, final BigInteger a, final ECPoint r, final ECPoint g) {
        if (r.equals(ECPoint.POINT_INFINITY)) {
            return g;
        }
        if (g.equals(ECPoint.POINT_INFINITY)) {
            return r;
        }
        if (r == g || r.equals(g)) {
            return doublePoint(p, a, r);
        }
        final BigInteger gX = g.getAffineX();
        final BigInteger sY = g.getAffineY();
        final BigInteger rX = r.getAffineX();
        final BigInteger rY = r.getAffineY();
        final BigInteger slope = (rY.subtract(sY)).multiply(rX.subtract(gX).modInverse(p)).mod(p);
        final BigInteger Xout = (slope.modPow(FieldP_2, p).subtract(rX)).subtract(gX).mod(p);
        BigInteger Yout = sY.negate().mod(p);
        Yout = Yout.add(slope.multiply(gX.subtract(Xout))).mod(p);
        return new ECPoint(Xout, Yout);
    }

    public static ECPoint scalmultNew(final ECParameterSpec params, final ECPoint g, final BigInteger kin) {
        EllipticCurve curve = params.getCurve();
        final ECField field = curve.getField();
        if (!(field instanceof ECFieldFp)) {
            throw new UnsupportedOperationException(field.getClass().getCanonicalName());
        }
        final BigInteger p = ((ECFieldFp) field).getP();
        final BigInteger a = curve.getA();
        ECPoint R = ECPoint.POINT_INFINITY;
        // value only valid for curve secp256k1, code taken from https://www.secg.org/sec2-v2.pdf,
        // see "Finally the order n of G and the cofactor are: n = "FF.."
        BigInteger SECP256K1_Q = params.getOrder();
        //BigInteger SECP256K1_Q = new BigInteger("00FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",16);
        BigInteger k = kin.mod(SECP256K1_Q); // uses this !
        // BigInteger k = kin.mod(p); // do not use this ! wrong as per comment from President James Moveon Polk
        final int length = k.bitLength();
        final byte[] binarray = new byte[length];
        for (int i = 0; i <= length - 1; i++) {
            binarray[i] = k.mod(FieldP_2).byteValue();
            k = k.shiftRight(1);
        }
        for (int i = length - 1; i >= 0; i--) {
            R = doublePoint(p, a, R);
            if (binarray[i] == 1) {
                R = addPoint(p, a, R, g);
            }
        }
        return R;
    }

    public static ECPoint scalmultOrg(final EllipticCurve curve, final ECPoint g, final BigInteger kin) {
        final ECField field = curve.getField();
        if (!(field instanceof ECFieldFp)) {
            throw new UnsupportedOperationException(field.getClass().getCanonicalName());
        }
        final BigInteger p = ((ECFieldFp) field).getP();
        final BigInteger a = curve.getA();
        ECPoint R = ECPoint.POINT_INFINITY;
        // value only valid for curve secp256k1, code taken from https://www.secg.org/sec2-v2.pdf,
        // see "Finally the order n of G and the cofactor are: n = "FF.."
        BigInteger SECP256K1_Q = new BigInteger("00FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
        BigInteger k = kin.mod(SECP256K1_Q); // uses this !
        // wrong as per comment from President James Moveon Polk
        // BigInteger k = kin.mod(p); // do not use this !
        System.out.println(" SECP256K1_Q: " + SECP256K1_Q);
        System.out.println("           p: " + p);
        System.out.println("curve: " + curve.toString());
        final int length = k.bitLength();
        final byte[] binarray = new byte[length];
        for (int i = 0; i <= length - 1; i++) {
            binarray[i] = k.mod(FieldP_2).byteValue();
            k = k.shiftRight(1);
        }
        for (int i = length - 1; i >= 0; i--) {
            R = doublePoint(p, a, R);
            if (binarray[i] == 1) {
                R = addPoint(p, a, R, g);
            }
        }
        return R;
    }

    public static ECPublicKey getPublicKey(final ECPrivateKey pk) throws GeneralSecurityException {
        final ECParameterSpec params = pk.getParams();
        final ECPoint w = scalmultNew(params, pk.getParams().getGenerator(), pk.getS());
        //final ECPoint w = scalmult(params.getCurve(), pk.getParams().getGenerator(), pk.getS());
        final KeyFactory kg = KeyFactory.getInstance("EC");
        return (ECPublicKey) kg.generatePublic(new ECPublicKeySpec(w, params));
    }
}
