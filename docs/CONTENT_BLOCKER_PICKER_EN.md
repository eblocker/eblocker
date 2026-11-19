# Help: Video Ads & Content Blocker and Element Picker

The device must be protected by eBlocker. On HTTPS sites, HTTPS support must work for that device; an HTTPS app exception can prevent content modification for the affected site.

## Hide an element on a website

1. Open the website and the orange eBlocker ControlBar. Choose **Pick element**.
2. Click the element. For covered elements, use **Underneath** or select a parent element. Check the orange outline and the proposed domain-specific rule.
3. **Preview** temporarily shows the result. **Select again** discards the selection.
4. In the eBlocker dialog, click **Check**. The rule must match the current domain and exactly one element on the current page. Hiding a particularly large area requires an additional confirmation.
5. Click **Apply**. eBlocker stores the rule once in **Custom element hiding rules**. A duplicate does not create a second entry.
6. After a successful save, the dialog closes, the rule field clears, and the picker is ready for the next element. Press **Esc** or **Close** to exit.

The picker language follows the browser language: German for `de`/`de-*`, English otherwise. Changing only the eBlocker Settings language does not control it.

## Manage custom rules

Go to **Settings → Blocker → Video Ads & Content Blocker → Custom element hiding rules** to inspect and edit the rules or toggle the entire custom-rule area. A rule may look like `www.example.org##.notice`. Check the domain and CSS selector before saving. Long generated IDs and `nth-of-type(...)` selectors may become ineffective or too broad after a site update.

The picker checks the page as it exists now. It cannot guarantee that a rule remains suitable after a website update. If content disappears, disable the most recently changed custom rule and reload the page.

## Add another content list

Under **Settings → Blocker → Video Ads & Content Blocker → Add**, enter a name and choose **Maintain your own list** (one rule per line) or **Provide a download URL**. **easylist** is fixed as the format because no other format is currently offered here. Save the list and check its switch.

This area processes supported cosmetic `##` rules, exceptions `#@#`, and available `+js(...)` scriptlet rules. Network rules such as `||example.org^` are not applied in this area. A list can therefore show **READY** even when it contains no effective content rule here. Start with a small list and check affected sites.

The built-in **uBlock Filters** list and your own lists have separate switches. **Custom element hiding rules** are also separate. Disabling a list does not delete its contents.

## Scriptlets 2.0

The Scriptlets 2.0 build includes a pinned selection of uBlock scriptlets that eBlocker injects into inspected HTML without a browser extension. Some older names remain as fallbacks. Not every uBlock feature or website is supported. Operation also depends on HTTPS support and the site's Content Security Policy. A scriptlet test is meaningful only when an enabled list contains a matching domain rule.

## If a site stops working

Record the URL and time. First check browser extensions and browser-local rules. Then, one at a time, disable the most recently changed custom rules, the affected content list, and other filters; reload after **each individual change**. Restore the original settings afterwards. A redirect to `bild.de/adblockwall.html` indicates blocker detection but does not identify one particular eBlocker rule.

For `ERR_ICAP_FAILURE` or connection interruptions, record the time and device and inspect eBlocker and network status. ICAP may briefly be unavailable during a service restart. Repeated errors need separate service and network diagnosis.
