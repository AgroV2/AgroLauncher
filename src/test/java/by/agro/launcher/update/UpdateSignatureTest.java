package by.agro.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class UpdateSignatureTest {
    @TempDir Path temp;
    private static byte[] manifest(String version, String artifact, String hash) {
        return ("AgroLauncher-update-v1\nversion=" + version + "\nartifact=" + artifact +
                "\nsize=123\nsha256=" + hash + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    private static byte[] sign(PrivateKey key, byte[] msg) throws Exception {
        var signature=Signature.getInstance("Ed25519"); signature.initSign(key); signature.update(msg); return signature.sign();
    }
    @Test void validSignedManifestBindsVersionNameSizeAndDigest() throws Exception {
        var kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] data=manifest("1.5.6", "AgroLauncher.jar", "a".repeat(64));
        var info=UpdateSignature.verify(data,sign(kp.getPrivate(),data),kp.getPublic(),"1.5.6","AgroLauncher.jar");
        assertEquals(123,info.size()); assertEquals("a".repeat(64),info.sha256());
    }
    @Test void rejectsWrongKeyTamperingAndReplayedMetadata() throws Exception {
        var kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); var other=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] data=manifest("1.5.6","AgroLauncher.jar","a".repeat(64)); byte[] sig=sign(kp.getPrivate(),data);
        assertThrows(IOException.class,()->UpdateSignature.verify(data,sig,other.getPublic(),"1.5.6","AgroLauncher.jar"));
        assertThrows(IOException.class,()->UpdateSignature.verify(manifest("1.5.6","AgroLauncher.jar","b".repeat(64)),sig,kp.getPublic(),"1.5.6","AgroLauncher.jar"));
        assertThrows(IOException.class,()->UpdateSignature.verify(data,sig,kp.getPublic(),"1.5.7","AgroLauncher.jar"));
        assertThrows(IOException.class,()->UpdateSignature.verify(data,sig,kp.getPublic(),"1.5.6","other.jar"));
        assertThrows(IOException.class,()->UpdateSignature.verify(data,new byte[0],kp.getPublic(),"1.5.6","AgroLauncher.jar"));
    }
    @Test void rejectsMalformedEvenWhenSignedAndAcceptsX509Pem() throws Exception {
        var kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String pem="-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(kp.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n";
        var key=UpdateSignature.readPublicKey(pem); byte[] bad="bad manifest".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IOException.class,()->UpdateSignature.verify(bad,sign(kp.getPrivate(),bad),key,"1.5.6","AgroLauncher.jar"));
        assertThrows(IOException.class,()->UpdateSignature.readPublicKey("invalid"));
    }
    @Test void stagesOnlyCorrectSignedJarAndKeepsInstalledLauncherOnFailures() throws Exception {
        var kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path candidate=temp.resolve("candidate.jar");
        var attributes=new java.util.jar.Manifest();
        attributes.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION,"1.0");
        attributes.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS,"example.Main");
        try(var out=new java.util.jar.JarOutputStream(Files.newOutputStream(candidate),attributes)) { }
        byte[] jar=Files.readAllBytes(candidate);
        String hash=by.agro.launcher.core.HashUtil.sha256(candidate);
        String future = by.agro.launcher.core.LauncherVersion.VERSION + ".1";
        byte[] metadata=("AgroLauncher-update-v1\nversion="+future+"\nartifact=AgroLauncher.jar\nsize="+jar.length+"\nsha256="+hash+"\n").getBytes(StandardCharsets.US_ASCII);
        byte[] signature=sign(kp.getPrivate(),metadata);
        var asset=new UpdateChecker.Asset("AgroLauncher.jar","https://example.test/jar",jar.length);
        var mf=new UpdateChecker.Asset("AgroLauncher.jar.manifest","https://example.test/manifest",metadata.length);
        var sf=new UpdateChecker.Asset("AgroLauncher.jar.manifest.sig","https://example.test/signature",signature.length);
        var release=new UpdateChecker.Release(future,"https://example.test",asset,null,mf,sf);
        Path installed=temp.resolve("AgroLauncher.jar"); Files.writeString(installed,"old launcher");
        var installation=new UpdateManager.Installation(installed,false,"test");
        var source=new UpdateManager.SignedUpdateSource() {
            public byte[] read(String u,long limit) { return u.endsWith("signature") ? signature : metadata; }
            public void download(String u,Path target,String sha,long limit) throws IOException { Files.write(target,jar); }
        };
        var staged=UpdateManager.stageWithKey(release,installation,kp.getPublic(),source);
        assertEquals(hash,staged.sha256()); assertEquals("old launcher",Files.readString(installed));
        var other=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThrows(IOException.class,()->UpdateManager.stageWithKey(release,installation,other.getPublic(),source));
        var badSource=new UpdateManager.SignedUpdateSource() {
            public byte[] read(String u,long limit) {return u.endsWith("signature")?signature:metadata;}
            public void download(String u,Path target,String sha,long limit) throws IOException {Files.write(target,new byte[jar.length]);}
        };
        assertThrows(IOException.class,()->UpdateManager.stageWithKey(release,installation,kp.getPublic(),badSource));
        assertFalse(Files.exists(staged.stagedFile())); assertEquals("old launcher",Files.readString(installed));
        var replay=new UpdateChecker.Release(by.agro.launcher.core.LauncherVersion.VERSION + ".2","https://example.test",asset,null,mf,sf);
        assertThrows(IOException.class,()->UpdateManager.stageWithKey(replay,installation,kp.getPublic(),source));
    }

    @Test void missingSignatureCannotUseUnsignedUpdate() throws Exception {
        var jar=new UpdateChecker.Asset("AgroLauncher.jar","https://example.test/AgroLauncher.jar",100);
        var release=new UpdateChecker.Release(by.agro.launcher.core.LauncherVersion.VERSION + ".1","https://example.test",jar,null,null,null);
        assertThrows(IOException.class,()->UpdateManager.stage(null,release,new UpdateManager.Installation(temp.resolve("AgroLauncher.jar"),false,"test")));
        assertFalse(Files.exists(temp.resolve("AgroLauncher.jar.update")));
    }
    @Test void modifiedStagedUpdateIsRejectedBeforeRestart() throws Exception {
        Path old=temp.resolve("AgroLauncher.jar"),staged=temp.resolve("AgroLauncher.jar.update");
        Files.writeString(old,"old"); Files.writeString(staged,"evil");
        var u=new UpdateManager.StagedUpdate(null,new UpdateManager.Installation(old,false,"test"),staged,"a".repeat(64),4);
        assertThrows(IOException.class,()->UpdateManager.installAndRestart(u,java.util.List.of()));
        assertEquals("old",Files.readString(old));
    }
}
