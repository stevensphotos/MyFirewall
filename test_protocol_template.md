Protocole de Test : MyFirewall (V1.0)
Objectif : Valider la stabilité, la sécurité et l'ergonomie de l'application MyFirewall sur des appareils Android (API 24 à 35).
1. Prérequis pour le testeur
•
Un appareil Android physique (les émulateurs ne gèrent pas correctement le VPN et la biométrie).
•
Accès Root (optionnel, pour tester le filtre Hosts).
•
Empreinte digitale ou code PIN configuré sur l'appareil.
•
Connexion Wi-Fi et Données Mobiles fonctionnelles.
2. Test du Cycle de Vie et Sécurité
T.01 : Premier Lancement (Onboarding)
•
Action : Installer et ouvrir l'application pour la première fois.
•
Attendu : L'écran d'Onboarding s'affiche. Le bouton "Start" demande les permissions nécessaires. L'application ne doit pas demander de verrouillage biométrique lors de cette phase.
T.02 : Verrouillage Biométrique (App Lock)
•
Action : Activer "Application Lock" dans l'onglet Settings. Fermer l'application (tuer le processus) et la relancer.
•
Attendu : Une invite biométrique système apparaît immédiatement. L'interface de l'application reste masquée tant que l'authentification n'est pas réussie.
•
Action : Annuler l'authentification.
•
Attendu : L'application doit se fermer immédiatement (finish()).
T.03 : Délai de Verrouillage (Timeout)
•
Action : Authentifier l'accès. Mettre l'application en arrière-plan pendant plus de 30 secondes. Revenir sur l'application.
•
Attendu : L'application doit redemander une authentification biométrique.
3. Test des Moteurs de Filtrage (Mutual Exclusivity)
T.04 : URL Filter (VPN)
•
Action : Aller à l’écran Rules cocher les listes de blocages, et vérifier sur l’écran Status le téléchargement effectif des listes de blocage et leur activation.
•
Attendu : Les listes de blocage doivent télécharger sans messages d’erreur.
•
Action : Activer "URL Filter VPN". Accepter la demande de connexion VPN d'Android.
•
Attendu : L'icône VPN apparaît dans la barre de statut. Le statut dans l'onglet Status passe à "Active".
•
Vérification : Tenter d'accéder à un domaine bloqué (ex: doubleclick.net). L'accès doit être refusé.
T.05 : Application Filter (Routage par App)
•
Action : Activer "Application Filter".
•
Attendu : Le "URL Filter VPN" doit se désactiver automatiquement (si actif). Le service VPN redémarre proprement.
•
Action : Dans l'onglet Applications, interdire l'accès Wi-Fi à un navigateur (ex: Chrome).
•
Attendu : Le navigateur ne peut plus charger de pages en Wi-Fi mais doit fonctionner en Données Mobiles si le toggle correspondant est ON.
T.06 : Transition Root (Hosts)
•
Action : Activer "URL Filter Root" (nécessite un appareil rooté).
•
Attendu : Le service VPN doit s'arrêter immédiatement. Une demande de super-utilisateur (SU) doit apparaître. "Start on Boot" doit passer à OFF.
•
Vérification : Ouvrir un terminal ou un explorateur de fichiers root et vérifier que /etc/hosts contient les domaines de la base de données.
4. Test de l'Interface Utilisateur (Accessibilité)
T.07 : Grandes Polices (Font Scaling)
•
Action : Dans les paramètres Android, régler la taille de la police au maximum (200%).
•
Attendu :
◦
Dans Settings, les boutons Switch doivent rester visibles à droite (pas de débordement).
◦
Dans la barre de navigation, les labels ne doivent pas être coupés ou sur deux lignes (ils doivent utiliser l'ellipse ... ou rester sur une ligne).
T.08 : Thèmes et Couleurs
•
Action : Basculer entre Light, Dark et System. Activer "Pure Black".
•
Attendu : L'UI s'adapte instantanément. En mode "Pure Black", le fond doit être #000000 (OLED).
5. Test de Persistance et Maintenance
T.09 : Start on Boot
•
Action : Activer un filtre VPN et cocher "Start on Boot". Redémarrer l'appareil.
•
Attendu : Après le déverrouillage de l'appareil (Direct Boot), le service VPN doit se lancer automatiquement sans intervention manuelle.
T.10 : Import / Export de Règles
•
Action : Exporter les règles dans un fichier JSON. Supprimer une règle manuellement. Réimporter le fichier.
•
Attendu : Un Toast confirme l'importation. La base de données Room est mise à jour. Les doublons doivent être gérés (remplacement).
6. Rapport d'Anomalie (Template)
•
ID : (ex: BUG-001)
•
Sévérité : (Bloquant / Majeur / Mineur)
•
Composant : (VPN / UI / Root / Sécurité)
•
Étapes de reproduction :
•
Résultat observé :
•
Résultat attendu :
•
Logs (Logcat) : (Optionnel mais recommandé pour les erreurs PermissionMonitor).

----------------------------------------------

Protocole de Test : MyFirewall (V1.0)
Objectif : Valider l'intégrité du filtrage réseau, la persistance des données Room et l'accessibilité de l'interface.
1. Tests de l'Interface Utilisateur (UI) & Navigation
1.1 Navigation et Pager
•
Action : Balayer (swipe) entre les onglets Status, Applications, Rules et Settings.
•
Attendu : Transition fluide. Le label de l'onglet sélectionné dans la NavigationSuiteScaffold doit être mis en évidence.
•
Test Accessibilité : Augmenter la police système à 200%. Les labels de navigation ne doivent pas créer d'orphelins ou déborder (vérification du plafonnement à 1.2x).
1.2 Sélection par Long Click (Contextuelle)
•
Action : Dans l'onglet Applications, effectuer un appui long sur une application de la liste.
•
Attendu : Ouverture immédiate des "Détails de l'application" dans les paramètres Android.
•
Action : Dans l'onglet Custom Rules, effectuer un appui long sur un domaine.
•
Attendu : Activation du mode sélection permettant la suppression groupée ou la modification.
2. Tests des Moteurs de Filtrage (VPN vs Hosts)
2.1 Filtrage URL via VPN
•
Action : Activer "URL Filter VPN" dans les Settings.
•
Attendu : Demande d'autorisation VPN système. Une fois acceptée, l'icône VPN apparaît.
•
Validation : Tenter d'accéder à un domaine présent dans la liste de blocage via un navigateur. La requête doit échouer (NXDOMAIN).
2.2 Filtrage URL via Hosts (Root)
•
Action : Activer "URL Filter Root".
•
Attendu :
i.
Désactivation automatique du VPN (Exclusivité).
ii.
Demande de permission SuperUtilisateur (SU).
iii.
Toggle "Start on Boot" passe à OFF.
•
Validation : Vérifier via un terminal (cat /etc/hosts) que les règles de la base de données Room ont été injectées.
2.3 Transition VPN vers Hosts
•
Action : Passer de l'un à l'autre rapidement.
•
Attendu : Pas de "Race Condition". Le delay(400) doit permettre au system_server de libérer l'interface tun0 avant que le moteur Hosts ne prenne le relais.
3. Test Pare-feu (Blocage par Application)
3.1 Blocage de trafic sortant
•
Action : Activer "Application Filter".
•
Action : Dans la liste, décocher l'icône Wi-Fi pour une application spécifique (ex: YouTube).
•
Attendu : L'application ne doit plus pouvoir charger de données en Wi-Fi.
•
Action : Décocher l'icône Mobile Data.
•
Attendu : L'application est totalement isolée du réseau.
•
Vérification technique : Vérifier dans les logs que le VpnPacketThread traite les paquets de l'UID concerné.
4. Importation / Exportation de listes
4.1 Exportation
•
Action : Cliquer sur "Export" -> "Application Rules".
•
Attendu : Ouverture du sélecteur de fichier (SAF). Le fichier JSON généré doit être valide et contenir les colonnes packageName, wifiAllowed, etc.
4.2 Importation et Sécurité (Validation JSON)
•
Action : Importer un fichier JSON malformé ou contenant un packageName invalide.
•
Attendu : Le filtre Regex doit rejeter l'entrée. Aucun crash ne doit survenir.
•
Action : Importer un fichier valide.
•
Attendu : Transaction Room atomique. Un Toast affiche le nombre de règles importées.
5. Gestion des listes Custom (Blacklist/Whitelist)
5.1 Ajout de domaines personnalisés
•
Action : Ajouter un domaine manuellement dans "Custom Rules".
•
Attendu : Validation du format (pas de caractères spéciaux interdits).
•
Action : Cliquer sur Sauvegarder.
•
Attendu : Si le VPN est actif, l'action ACTION_RELOAD_RULES doit être envoyée immédiatement pour mettre à jour le cache blockedDomains du service.
6. Tests de Sécurité (Biométrie)
6.1 Verrouillage au démarrage
•
Action : Activer "Application Lock". Tuer l'application et la relancer.
•
Attendu : BiometricPrompt s'affiche avant tout accès à l'UI.
•
Action : Annuler l'authentification.
•
Attendu : L'application appelle finish() et se ferme.
6.2 Timeout de session
•
Action : Déverrouiller l'app. Mettre l'app en arrière-plan pendant plus de 30 secondes. Revenir.
•
Attendu : Le LifecycleObserver doit détecter le dépassement du lockTimeout et redemander l'empreinte.
Rapport de Stabilité (Checklist Finale)
•
[ ] Aucun blocage du thread principal (ANR) lors des écritures SharedPreferences (.apply()).
•
[ ] Le thread VPN consomme moins de 2% CPU en veille.
•
[ ] Les icônes s'affichent correctement en mode Dark et Pure Black.
•
[ ] Pas de fuite mémoire lors du changement d'orientation (utilisation de rememberSaveable)