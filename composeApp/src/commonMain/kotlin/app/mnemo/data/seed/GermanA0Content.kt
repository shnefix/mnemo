package app.mnemo.data.seed

/**
 * Content of the built-in German A0 "Name it" deck: pictures of the first things to name, with the
 * article (der / die / das), the plural, and a very simple example. One card per line, fields
 * separated by `|`.
 *
 * Nouns: picture | word with article | plural ("–" = no plural) | English | example | translation
 * Verbs: picture | verb | ich-form | English | example | translation
 * Colours and numbers: picture | word | English | example | translation
 */
internal object GermanA0Content {
    const val THINGS = """
📕|das Buch|die Bücher|book|Das Buch ist gut.|The book is good.
✏️|der Bleistift|die Bleistifte|pencil|Ich habe einen Bleistift.|I have a pencil.
🖊️|der Kugelschreiber|die Kugelschreiber|pen|Ich brauche einen Kugelschreiber.|I need a pen.
✂️|die Schere|die Scheren|scissors|Die Schere ist scharf.|The scissors are sharp.
🔑|der Schlüssel|die Schlüssel|key|Wo ist mein Schlüssel?|Where is my key?
📱|das Handy|die Handys|mobile phone|Mein Handy ist neu.|My phone is new.
💻|der Computer|die Computer|computer|Der Computer ist an.|The computer is on.
📺|der Fernseher|die Fernseher|television|Der Fernseher ist laut.|The TV is loud.
⌚|die Uhr|die Uhren|watch, clock|Die Uhr ist alt.|The watch is old.
⏰|der Wecker|die Wecker|alarm clock|Der Wecker klingelt.|The alarm clock is ringing.
💡|das Licht|die Lichter|light|Das Licht ist an.|The light is on.
🚪|die Tür|die Türen|door|Die Tür ist offen.|The door is open.
🪟|das Fenster|die Fenster|window|Das Fenster ist zu.|The window is closed.
🛏️|das Bett|die Betten|bed|Das Bett ist weich.|The bed is soft.
🪑|der Stuhl|die Stühle|chair|Der Stuhl ist braun.|The chair is brown.
🛋️|das Sofa|die Sofas|sofa|Das Sofa ist bequem.|The sofa is comfortable.
🪞|der Spiegel|die Spiegel|mirror|Der Spiegel ist groß.|The mirror is big.
🧹|der Besen|die Besen|broom|Der Besen ist dort.|The broom is there.
🛁|die Badewanne|die Badewannen|bathtub|Die Badewanne ist voll.|The bathtub is full.
🚿|die Dusche|die Duschen|shower|Die Dusche ist kalt.|The shower is cold.
🪥|die Zahnbürste|die Zahnbürsten|toothbrush|Das ist meine Zahnbürste.|That is my toothbrush.
🧼|die Seife|die Seifen|soap|Die Seife riecht gut.|The soap smells good.
🕯️|die Kerze|die Kerzen|candle|Die Kerze brennt.|The candle is burning.
🔦|die Taschenlampe|die Taschenlampen|torch|Ich habe eine Taschenlampe.|I have a torch.
🎒|der Rucksack|die Rucksäcke|backpack|Mein Rucksack ist schwer.|My backpack is heavy.
👜|die Tasche|die Taschen|bag|Die Tasche ist leer.|The bag is empty.
☂️|der Regenschirm|die Regenschirme|umbrella|Ich habe einen Regenschirm.|I have an umbrella.
👓|die Brille|die Brillen|glasses|Ich brauche eine Brille.|I need glasses.
💍|der Ring|die Ringe|ring|Der Ring ist schön.|The ring is beautiful.
🎁|das Geschenk|die Geschenke|gift|Das Geschenk ist für dich.|The gift is for you.
💰|das Geld|–|money|Das Geld ist weg.|The money is gone.
📦|das Paket|die Pakete|parcel|Das Paket ist schwer.|The parcel is heavy.
✉️|der Brief|die Briefe|letter|Der Brief ist von Anna.|The letter is from Anna.
🗺️|die Karte|die Karten|map|Ich habe eine Karte.|I have a map.
📷|die Kamera|die Kameras|camera|Die Kamera ist neu.|The camera is new.
🎈|der Luftballon|die Luftballons|balloon|Der Luftballon ist rot.|The balloon is red.
⚽|der Fußball|die Fußbälle|football|Der Fußball ist rund.|The football is round.
🎸|die Gitarre|die Gitarren|guitar|Die Gitarre ist alt.|The guitar is old.
🎹|das Klavier|die Klaviere|piano|Das Klavier ist groß.|The piano is big.
🧸|der Teddybär|die Teddybären|teddy bear|Der Teddybär ist weich.|The teddy bear is soft.
👕|das T-Shirt|die T-Shirts|T-shirt|Das T-Shirt ist blau.|The T-shirt is blue.
👖|die Hose|die Hosen|trousers|Die Hose ist neu.|The trousers are new.
👗|das Kleid|die Kleider|dress|Das Kleid ist rot.|The dress is red.
🧥|die Jacke|die Jacken|jacket|Die Jacke ist warm.|The jacket is warm.
👟|der Schuh|die Schuhe|shoe|Der Schuh ist klein.|The shoe is small.
🧦|die Socke|die Socken|sock|Die Socke ist nass.|The sock is wet.
🧢|die Kappe|die Kappen|cap|Die Kappe ist schwarz.|The cap is black.
🧣|der Schal|die Schals|scarf|Der Schal ist lang.|The scarf is long.
🧤|der Handschuh|die Handschuhe|glove|Der Handschuh ist warm.|The glove is warm.
🔔|die Glocke|die Glocken|bell|Die Glocke ist laut.|The bell is loud.
🔥|das Feuer|die Feuer|fire|Das Feuer ist heiß.|The fire is hot.
"""

    const val FOOD = """
🍎|der Apfel|die Äpfel|apple|Der Apfel ist rot.|The apple is red.
🍌|die Banane|die Bananen|banana|Die Banane ist gelb.|The banana is yellow.
🍊|die Orange|die Orangen|orange|Die Orange ist süß.|The orange is sweet.
🍇|die Traube|die Trauben|grape|Ich esse Trauben.|I eat grapes.
🍓|die Erdbeere|die Erdbeeren|strawberry|Ich mag Erdbeeren.|I like strawberries.
🍉|die Wassermelone|die Wassermelonen|watermelon|Die Wassermelone ist groß.|The watermelon is big.
🍋|die Zitrone|die Zitronen|lemon|Die Zitrone ist sauer.|The lemon is sour.
🍅|die Tomate|die Tomaten|tomato|Die Tomate ist rot.|The tomato is red.
🥕|die Karotte|die Karotten|carrot|Ich esse eine Karotte.|I eat a carrot.
🥔|die Kartoffel|die Kartoffeln|potato|Ich esse Kartoffeln.|I eat potatoes.
🌽|der Mais|–|corn|Ich mag Mais.|I like corn.
🧅|die Zwiebel|die Zwiebeln|onion|Die Zwiebel ist groß.|The onion is big.
🥒|die Gurke|die Gurken|cucumber|Die Gurke ist grün.|The cucumber is green.
🍞|das Brot|die Brote|bread|Das Brot ist frisch.|The bread is fresh.
🥐|das Croissant|die Croissants|croissant|Das Croissant ist warm.|The croissant is warm.
🧀|der Käse|die Käse|cheese|Der Käse ist lecker.|The cheese is tasty.
🥚|das Ei|die Eier|egg|Das Ei ist kalt.|The egg is cold.
🥩|das Fleisch|–|meat|Ich esse kein Fleisch.|I don't eat meat.
🍗|das Hähnchen|die Hähnchen|chicken (meat)|Das Hähnchen ist lecker.|The chicken is tasty.
🍕|die Pizza|die Pizzen|pizza|Ich esse Pizza.|I eat pizza.
🍔|der Hamburger|die Hamburger|burger|Der Hamburger ist groß.|The burger is big.
🍟|die Pommes|die Pommes|chips, fries|Ich mag Pommes.|I like fries.
🍝|die Nudeln|die Nudeln|pasta, noodles|Ich esse gern Nudeln.|I like eating pasta.
🍚|der Reis|–|rice|Der Reis ist heiß.|The rice is hot.
🥗|der Salat|die Salate|salad|Der Salat ist frisch.|The salad is fresh.
🍰|der Kuchen|die Kuchen|cake|Der Kuchen ist süß.|The cake is sweet.
🍫|die Schokolade|die Schokoladen|chocolate|Ich mag Schokolade.|I like chocolate.
🍦|das Eis|–|ice cream|Das Eis ist kalt.|The ice cream is cold.
🥛|die Milch|–|milk|Die Milch ist kalt.|The milk is cold.
💧|das Wasser|–|water|Ich trinke Wasser.|I drink water.
☕|der Kaffee|die Kaffees|coffee|Der Kaffee ist heiß.|The coffee is hot.
🍵|der Tee|die Tees|tea|Der Tee ist heiß.|The tea is hot.
🧃|der Saft|die Säfte|juice|Der Saft ist süß.|The juice is sweet.
🍺|das Bier|die Biere|beer|Das Bier ist kalt.|The beer is cold.
🍷|der Wein|die Weine|wine|Der Wein ist rot.|The wine is red.
🥄|der Löffel|die Löffel|spoon|Der Löffel ist klein.|The spoon is small.
🔪|das Messer|die Messer|knife|Das Messer ist scharf.|The knife is sharp.
🍽️|der Teller|die Teller|plate|Der Teller ist leer.|The plate is empty.
🥣|die Schüssel|die Schüsseln|bowl|Die Schüssel ist voll.|The bowl is full.
🍳|die Pfanne|die Pfannen|frying pan|Die Pfanne ist heiß.|The frying pan is hot.
"""

    const val ANIMALS = """
🐶|der Hund|die Hunde|dog|Der Hund ist klein.|The dog is small.
🐱|die Katze|die Katzen|cat|Die Katze schläft.|The cat is sleeping.
🐭|die Maus|die Mäuse|mouse|Die Maus ist klein.|The mouse is small.
🐰|das Kaninchen|die Kaninchen|rabbit|Das Kaninchen ist weiß.|The rabbit is white.
🐻|der Bär|die Bären|bear|Der Bär ist groß.|The bear is big.
🦊|der Fuchs|die Füchse|fox|Der Fuchs ist schlau.|The fox is clever.
🐷|das Schwein|die Schweine|pig|Das Schwein ist rosa.|The pig is pink.
🐮|die Kuh|die Kühe|cow|Die Kuh gibt Milch.|The cow gives milk.
🐴|das Pferd|die Pferde|horse|Das Pferd ist schnell.|The horse is fast.
🐑|das Schaf|die Schafe|sheep|Das Schaf ist weiß.|The sheep is white.
🐔|das Huhn|die Hühner|chicken (bird)|Das Huhn legt Eier.|The chicken lays eggs.
🐦|der Vogel|die Vögel|bird|Der Vogel singt.|The bird is singing.
🦆|die Ente|die Enten|duck|Die Ente schwimmt.|The duck is swimming.
🦉|die Eule|die Eulen|owl|Die Eule schläft am Tag.|The owl sleeps during the day.
🐸|der Frosch|die Frösche|frog|Der Frosch ist grün.|The frog is green.
🐢|die Schildkröte|die Schildkröten|turtle|Die Schildkröte ist langsam.|The turtle is slow.
🐍|die Schlange|die Schlangen|snake|Die Schlange ist lang.|The snake is long.
🐟|der Fisch|die Fische|fish|Der Fisch schwimmt.|The fish is swimming.
🦈|der Hai|die Haie|shark|Der Hai ist gefährlich.|The shark is dangerous.
🐘|der Elefant|die Elefanten|elephant|Der Elefant ist groß.|The elephant is big.
🦁|der Löwe|die Löwen|lion|Der Löwe ist stark.|The lion is strong.
🐯|der Tiger|die Tiger|tiger|Der Tiger ist schnell.|The tiger is fast.
🐵|der Affe|die Affen|monkey|Der Affe isst eine Banane.|The monkey is eating a banana.
🦒|die Giraffe|die Giraffen|giraffe|Die Giraffe ist sehr groß.|The giraffe is very tall.
🐝|die Biene|die Bienen|bee|Die Biene macht Honig.|The bee makes honey.
🦋|der Schmetterling|die Schmetterlinge|butterfly|Der Schmetterling ist bunt.|The butterfly is colourful.
🐜|die Ameise|die Ameisen|ant|Die Ameise ist klein.|The ant is small.
🕷️|die Spinne|die Spinnen|spider|Die Spinne ist schwarz.|The spider is black.
🐌|die Schnecke|die Schnecken|snail|Die Schnecke ist langsam.|The snail is slow.
"""

    const val NATURE_BODY = """
☀️|die Sonne|–|sun|Die Sonne scheint.|The sun is shining.
🌙|der Mond|die Monde|moon|Der Mond ist hell.|The moon is bright.
⭐|der Stern|die Sterne|star|Der Stern ist hell.|The star is bright.
☁️|die Wolke|die Wolken|cloud|Die Wolke ist grau.|The cloud is grey.
🌧️|der Regen|–|rain|Der Regen ist kalt.|The rain is cold.
❄️|der Schnee|–|snow|Der Schnee ist weiß.|The snow is white.
🌈|der Regenbogen|die Regenbögen|rainbow|Der Regenbogen ist bunt.|The rainbow is colourful.
🌳|der Baum|die Bäume|tree|Der Baum ist hoch.|The tree is tall.
🌻|die Sonnenblume|die Sonnenblumen|sunflower|Die Sonnenblume ist gelb.|The sunflower is yellow.
🌹|die Rose|die Rosen|rose|Die Rose ist rot.|The rose is red.
🍂|das Blatt|die Blätter|leaf|Das Blatt ist braun.|The leaf is brown.
⛰️|der Berg|die Berge|mountain|Der Berg ist hoch.|The mountain is high.
🌊|das Meer|die Meere|sea|Das Meer ist blau.|The sea is blue.
🏖️|der Strand|die Strände|beach|Der Strand ist schön.|The beach is beautiful.
👁️|das Auge|die Augen|eye|Ich habe blaue Augen.|I have blue eyes.
👂|das Ohr|die Ohren|ear|Mein Ohr tut weh.|My ear hurts.
👃|die Nase|die Nasen|nose|Meine Nase ist kalt.|My nose is cold.
👄|der Mund|die Münder|mouth|Mach den Mund auf!|Open your mouth!
🦷|der Zahn|die Zähne|tooth|Der Zahn tut weh.|The tooth hurts.
🖐️|die Hand|die Hände|hand|Meine Hände sind kalt.|My hands are cold.
🦶|der Fuß|die Füße|foot|Mein Fuß tut weh.|My foot hurts.
🦵|das Bein|die Beine|leg|Das Bein ist lang.|The leg is long.
💪|der Arm|die Arme|arm|Mein Arm tut weh.|My arm hurts.
❤️|das Herz|die Herzen|heart|Das Herz schlägt schnell.|The heart is beating fast.
"""

    const val PEOPLE_PLACES = """
👶|das Baby|die Babys|baby|Das Baby schläft.|The baby is sleeping.
👦|der Junge|die Jungen|boy|Der Junge spielt.|The boy is playing.
👧|das Mädchen|die Mädchen|girl|Das Mädchen lacht.|The girl is laughing.
👨|der Mann|die Männer|man|Der Mann ist nett.|The man is nice.
👩|die Frau|die Frauen|woman|Die Frau ist nett.|The woman is nice.
🏠|das Haus|die Häuser|house|Das Haus ist groß.|The house is big.
🏫|die Schule|die Schulen|school|Die Schule ist groß.|The school is big.
🏥|das Krankenhaus|die Krankenhäuser|hospital|Das Krankenhaus ist hier.|The hospital is here.
⛪|die Kirche|die Kirchen|church|Die Kirche ist alt.|The church is old.
🏨|das Hotel|die Hotels|hotel|Das Hotel ist neu.|The hotel is new.
🏦|die Bank|die Banken|bank|Die Bank ist zu.|The bank is closed.
🚉|der Bahnhof|die Bahnhöfe|train station|Der Bahnhof ist hier.|The train station is here.
🚗|das Auto|die Autos|car|Das Auto ist rot.|The car is red.
🚌|der Bus|die Busse|bus|Der Bus kommt.|The bus is coming.
🚲|das Fahrrad|die Fahrräder|bicycle|Ich habe ein Fahrrad.|I have a bicycle.
🚆|der Zug|die Züge|train|Der Zug ist schnell.|The train is fast.
✈️|das Flugzeug|die Flugzeuge|plane|Das Flugzeug fliegt.|The plane is flying.
🚢|das Schiff|die Schiffe|ship|Das Schiff ist groß.|The ship is big.
🚑|der Krankenwagen|die Krankenwagen|ambulance|Der Krankenwagen kommt.|The ambulance is coming.
🚦|die Ampel|die Ampeln|traffic light|Die Ampel ist rot.|The traffic light is red.
"""

    const val ACTIONS = """
😋|essen|ich esse|to eat|Ich esse einen Apfel.|I am eating an apple.
🥤|trinken|ich trinke|to drink|Ich trinke Wasser.|I drink water.
😴|schlafen|ich schlafe|to sleep|Ich schlafe gern.|I like sleeping.
🚶|gehen|ich gehe|to go, to walk|Ich gehe zur Schule.|I go to school.
🏃|rennen|ich renne|to run|Ich renne schnell.|I run fast.
🏊|schwimmen|ich schwimme|to swim|Ich schwimme im Meer.|I swim in the sea.
📖|lesen|ich lese|to read|Ich lese ein Buch.|I am reading a book.
✍️|schreiben|ich schreibe|to write|Ich schreibe einen Brief.|I am writing a letter.
🧑‍🍳|kochen|ich koche|to cook|Ich koche Nudeln.|I am cooking pasta.
💃|tanzen|ich tanze|to dance|Ich tanze gern.|I like dancing.
🧑‍🎤|singen|ich singe|to sing|Ich singe ein Lied.|I sing a song.
😂|lachen|ich lache|to laugh|Ich lache viel.|I laugh a lot.
😭|weinen|ich weine|to cry|Das Baby weint.|The baby is crying.
🗣️|sprechen|ich spreche|to speak|Ich spreche Deutsch.|I speak German.
🎧|hören|ich höre|to listen, to hear|Ich höre Musik.|I am listening to music.
👀|sehen|ich sehe|to see|Ich sehe einen Vogel.|I see a bird.
🚴|fahren|ich fahre|to ride, to drive|Ich fahre Fahrrad.|I ride a bicycle.
🛫|fliegen|ich fliege|to fly|Ich fliege nach Berlin.|I am flying to Berlin.
🎮|spielen|ich spiele|to play|Ich spiele gern.|I like playing.
🛍️|kaufen|ich kaufe|to buy|Ich kaufe Brot.|I am buying bread.
🧑‍💻|arbeiten|ich arbeite|to work|Ich arbeite heute.|I am working today.
🧑‍🎓|lernen|ich lerne|to learn|Ich lerne Deutsch.|I am learning German.
⏳|warten|ich warte|to wait|Ich warte auf den Bus.|I am waiting for the bus.
🔓|öffnen|ich öffne|to open|Ich öffne die Tür.|I open the door.
🔒|schließen|ich schließe|to close, to lock|Ich schließe das Fenster.|I close the window.
🧍|stehen|ich stehe|to stand|Ich stehe hier.|I am standing here.
👋|winken|ich winke|to wave|Ich winke dir.|I wave at you.
👏|klatschen|ich klatsche|to clap|Ich klatsche laut.|I clap loudly.
🫂|umarmen|ich umarme|to hug|Ich umarme meine Mama.|I hug my mum.
📞|telefonieren|ich telefoniere|to make a phone call|Ich telefoniere mit Anna.|I am on the phone with Anna.
🤔|denken|ich denke|to think|Ich denke an dich.|I am thinking of you.
🎨|malen|ich male|to paint, to draw|Ich male ein Bild.|I am painting a picture.
📸|fotografieren|ich fotografiere|to take photos|Ich fotografiere den Hund.|I am photographing the dog.
"""

    const val COLOURS_NUMBERS = """
🔴|rot|red|Der Apfel ist rot.|The apple is red.
🟠|orange|orange|Die Orange ist orange.|The orange is orange.
🟡|gelb|yellow|Die Banane ist gelb.|The banana is yellow.
🟢|grün|green|Der Frosch ist grün.|The frog is green.
🔵|blau|blue|Das Meer ist blau.|The sea is blue.
🟣|lila|purple|Die Blume ist lila.|The flower is purple.
🟤|braun|brown|Der Bär ist braun.|The bear is brown.
⚫|schwarz|black|Die Katze ist schwarz.|The cat is black.
⚪|weiß|white|Der Schnee ist weiß.|The snow is white.
🩶|grau|grey|Die Wolke ist grau.|The cloud is grey.
🩷|rosa|pink|Das Schwein ist rosa.|The pig is pink.
1️⃣|eins|one (1)|Eins, zwei, drei!|One, two, three!
2️⃣|zwei|two (2)|Ich habe zwei Katzen.|I have two cats.
3️⃣|drei|three (3)|Wir sind drei.|There are three of us.
4️⃣|vier|four (4)|Das Auto hat vier Räder.|The car has four wheels.
5️⃣|fünf|five (5)|Eine Hand hat fünf Finger.|A hand has five fingers.
6️⃣|sechs|six (6)|Ein Würfel hat sechs Seiten.|A die has six sides.
7️⃣|sieben|seven (7)|Eine Woche hat sieben Tage.|A week has seven days.
8️⃣|acht|eight (8)|Eine Spinne hat acht Beine.|A spider has eight legs.
9️⃣|neun|nine (9)|Es ist neun Uhr.|It is nine o'clock.
🔟|zehn|ten (10)|Ich bin zehn Jahre alt.|I am ten years old.
"""
}
