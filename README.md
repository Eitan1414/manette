# Manette

Manette transforme une tablette Android en manette virtuelle pour une vraie Wii U sous Aroma.

## Architecture

- `android/` : application Android tactile (APK)
- `wiiu/` : plugin Aroma/WUPS (WPS)
- `docs/` : protocole réseau et notes de développement

La tablette envoie l'état le plus récent de la manette en UDP sur le réseau local. Le plugin Wii U reçoit cet état et l'expose comme une Wii U Pro Controller virtuelle.

## Objectif V0.1

- A/B/X/Y
- croix directionnelle
- L/R/ZL/ZR
- + / - / HOME
- deux sticks analogiques + clics sticks
- connexion par IP locale
- éditeur de disposition en temps réel
- déplacement individuel des commandes
- redimensionnement individuel des commandes
- sauvegarde/restauration du layout
- plugin Aroma ciblant le joueur 1

## Sécurité de latence

Le protocole ne met pas les entrées en file d'attente. Chaque paquet remplace l'état précédent : si un paquet est perdu ou arrive trop tard, l'état le plus récent gagne.

## Statut

Première base de développement en cours. Ne pas considérer la V0.1 comme prête pour une utilisation quotidienne tant que les tests sur vraie Wii U n'ont pas validé l'injection KPAD/WPAD et la latence.
