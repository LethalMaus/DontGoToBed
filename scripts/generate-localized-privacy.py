#!/usr/bin/env python3
"""Render platform-specific translations of the existing privacy policy, without altering its terms."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
permissions={
'de':[
'Android-Berechtigungen für Internet und Netzwerkstatus ermöglichen lokalen Mehrspieler und freiwillige Käufe. Die App fordert keinen Standort, Kontakte, Kamera, Mikrofon oder Fotos an. Spieleinstellungen können je nach Geräteeinstellungen in Android-Sicherungen oder Geräteübertragungen enthalten sein. Caches und Kennungen des Kauf-SDK sind von den Android-Sicherungsregeln der App ausgeschlossen.',
'iOS fragt bei Bedarf für lokalen Mehrspieler nach Zugriff auf das lokale Netzwerk. Du kannst dies in den Einstellungen ändern. Einstellungen und Kauf-SDK-Caches können gemäß deinen Apple-Einstellungen in Gerätesicherungen enthalten sein. Die App fordert keinen Zugriff auf Standort, Kontakte, Kamera, Mikrofon, Fotos oder Werbetracking an.'],
'es':[
'Los permisos de Internet y estado de red de Android permiten el multijugador cercano y las compras opcionales. La aplicación no solicita ubicación, contactos, cámara, micrófono ni fotos. Las preferencias pueden incluirse en las copias de seguridad o transferencias de Android según tus ajustes. Las cachés e identificadores del SDK de compras se excluyen de las reglas de copia de seguridad de Android.',
'iOS solicita permiso de red local cuando es necesario para el multijugador cercano. Puedes cambiarlo en Ajustes. Las preferencias y cachés del SDK de compras pueden incluirse en copias de seguridad según tus ajustes de Apple. La aplicación no solicita ubicación, contactos, cámara, micrófono, fotos ni seguimiento publicitario.'],
'it':[
'I permessi Internet e stato della rete di Android consentono multigiocatore locale e acquisti facoltativi. L’app non richiede posizione, contatti, fotocamera, microfono o foto. Le preferenze possono essere incluse nei backup o trasferimenti Android secondo le impostazioni del dispositivo. Cache e identificativi del SDK acquisti sono esclusi dalle regole di backup Android.',
'iOS richiede il permesso di rete locale quando serve per il multigiocatore. Puoi modificarlo in Impostazioni. Preferenze e cache del SDK acquisti possono essere incluse nei backup secondo le impostazioni Apple. L’app non richiede posizione, contatti, fotocamera, microfono, foto o tracciamento pubblicitario.'],
'fr':[
'Les autorisations Internet et d’état du réseau Android permettent le multijoueur local et les achats facultatifs. L’app ne demande ni localisation, ni contacts, ni appareil photo, ni microphone ou photos. Les préférences peuvent être incluses dans les sauvegardes ou transferts Android selon vos réglages. Les caches et identifiants du SDK d’achat sont exclus des règles de sauvegarde Android.',
'iOS demande l’autorisation du réseau local lorsque le multijoueur en a besoin. Vous pouvez la modifier dans Réglages. Les préférences et caches du SDK d’achat peuvent être inclus dans les sauvegardes selon vos réglages Apple. L’app ne demande ni localisation, ni contacts, ni appareil photo, ni microphone, ni photos ou suivi publicitaire.'],
'pt':[
'As permissões de Internet e estado de rede do Android permitem o multijogador local e compras opcionais. O app não solicita localização, contatos, câmera, microfone ou fotos. As preferências podem entrar nos backups ou transferências do Android conforme suas configurações. Caches e identificadores do SDK de compras são excluídos das regras de backup Android.',
'O iOS solicita permissão de rede local quando necessário para o multijogador. Ela pode ser alterada em Configurações. Preferências e caches do SDK de compras podem entrar nos backups conforme seus ajustes Apple. O app não solicita localização, contatos, câmera, microfone, fotos ou rastreamento publicitário.'],
'ru':[
'Разрешения Android на Интернет и состояние сети нужны для локальной сетевой игры и добровольных покупок. Приложение не запрашивает местоположение, контакты, камеру, микрофон или фотографии. Настройки игры могут включаться в резервные копии Android или перенос устройства согласно вашим настройкам. Кэши и идентификаторы SDK покупок исключены из правил резервного копирования Android.',
'iOS запрашивает доступ к локальной сети, когда он нужен для сетевой игры поблизости. Его можно изменить в настройках. Настройки и кэши SDK покупок могут включаться в резервные копии согласно параметрам Apple. Приложение не запрашивает доступ к местоположению, контактам, камере, микрофону, фотографиям или рекламному отслеживанию.']}
for lang,perm in permissions.items():
 template=(ROOT/f'docs/localization/privacy/{lang}.txt').read_text()
 for i,(platform,name,store,url) in enumerate([('android','Android','Google Play','https://policies.google.com/privacy'),('ios','iOS / iPadOS','Apple App Store','https://www.apple.com/legal/privacy/')]):
  content=template.format(platform=name,store=store,permissions=perm[i],providerPolicy=f'{store}: {url}')
  (ROOT/f'composeApp/src/commonMain/composeResources/files/privacy-{platform}-{lang}.txt').write_text(content)
print('Generated 12 complete localized privacy documents.')
