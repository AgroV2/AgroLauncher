package by.agro.launcher.ui.components;

import by.agro.launcher.auth.Account;
import org.junit.jupiter.api.Test;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AccountHeadIconsTest {
    @Test void upgradesOnlyKnownSkinHosts() {
        assertEquals("https://skinsystem.ely.by/skins/player.png", AccountHeadIcons.normalizeUrl("http://skinsystem.ely.by/skins/player.png"));
        assertEquals("https://ely.by/storage/skins/abc.png", AccountHeadIcons.normalizeUrl("http://ely.by/storage/skins/abc.png"));
        assertEquals("", AccountHeadIcons.normalizeUrl("http://ely.by.evil.example/storage/skins/abc.png"));
        assertEquals("https://textures.minecraft.net/texture/abc", AccountHeadIcons.normalizeUrl("http://textures.minecraft.net/texture/abc"));
        assertEquals("", AccountHeadIcons.normalizeUrl("http://other.example/skin.png"));
        assertEquals("", AccountHeadIcons.normalizeUrl("http://skinsystem.ely.by.evil.example/a"));
        assertEquals("", AccountHeadIcons.normalizeUrl("http://user@skinsystem.ely.by/a"));
        assertEquals("", AccountHeadIcons.normalizeUrl("http://skinsystem.ely.by:8080/a"));
        assertEquals("https://example.com/a", AccountHeadIcons.normalizeUrl("//example.com/a"));
    }

    @Test void fallsBackWhenPrimaryThrows() throws Exception {
        List<String> calls = new ArrayList<>();
        Icon expected = new ImageIcon();
        Icon actual = AccountHeadIcons.loadWithFallback("https://primary", "https://fallback", url -> {
            calls.add(url);
            if (url.endsWith("primary")) throw new IOException("unavailable");
            return expected;
        });
        assertSame(expected, actual);
        assertEquals(List.of("https://primary", "https://fallback"), calls);
    }

    @Test void fallsBackOnEmptyImageAndDoesNotRetrySameUrl() throws Exception {
        List<String> calls = new ArrayList<>();
        Icon result = AccountHeadIcons.loadWithFallback("https://same", "https://same", url -> {
            calls.add(url); return null;
        });
        assertNull(result);
        assertEquals(List.of("https://same"), calls);
        Icon expected = new ImageIcon();
        assertSame(expected, AccountHeadIcons.loadWithFallback("https://primary", "https://fallback",
                url -> url.endsWith("primary") ? null : expected));
    }

    @Test void invalidPrimaryResolvesToElyFallback() {
        Account account = new Account();
        account.type = Account.Type.ELY_BY;
        account.username = "Player";
        account.skinUrl = "http://untrusted.example/skin.png";
        assertEquals("https://skinsystem.ely.by/skins/Player.png", AccountHeadIcons.resolveSkinUrl(account, null));
    }
}
