# Certificate verification wordlist v1

`bip39-en-v1.txt` is the unchanged 2048-word English BIP-39 list from the
Bitcoin BIPs repository, downloaded 2026-11-09:
https://github.com/bitcoin/bips/blob/99e21c81547bc14445d9a2083647f19621e3a840/bip-0039/english.txt

SHA-256: `2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda`.
Wire version: `bip39-en-v1`. Ordering is protocol data and must never change
under this version. Pinned source revision: `99e21c81547bc14445d9a2083647f19621e3a840`.
License: MIT, per the source BIP-39 document at that same revision. See LICENSE.

This uses six consecutive, most-significant-first 11-bit indices from the
first 66 bits of the SHA-256 digest of the complete DER certificate. It does
not use the BIP-39 mnemonic seed, entropy, or checksum algorithm. Verification
words are derived locally from the probed certificate; they are never trusted
from a server response.
