package app.mnemo.data.seed

/**
 * Content of the built-in "German (A1)" deck: the present tense of the common verbs, word order
 * and questions. One card per line, fields separated by `|`.
 *
 * Verbs: infinitive | English | ich | du | er/sie | example | translation
 * Word order: the words (the first one starts the sentence) separated by " / " | the sentence | the rule | translation
 * Questions: situation | the question | note | example answer | translation
 */
internal object GermanA1Content {
    const val VERBS = """
sein|to be|bin|bist|ist|Ich bin müde.|I am tired.
haben|to have|habe|hast|hat|Er hat einen Hund.|He has a dog.
werden|to become|werde|wirst|wird|Es wird kalt.|It's getting cold.
wohnen|to live|wohne|wohnst|wohnt|Sie wohnt in Berlin.|She lives in Berlin.
heißen|to be called|heiße|heißt|heißt|Er heißt Tom.|His name is Tom.
kommen|to come|komme|kommst|kommt|Woher kommst du?|Where do you come from?
machen|to do, to make|mache|machst|macht|Was machst du?|What are you doing?
lernen|to learn|lerne|lernst|lernt|Ich lerne Deutsch.|I am learning German.
arbeiten|to work|arbeite|arbeitest|arbeitet|Er arbeitet heute.|He works today.
spielen|to play|spiele|spielst|spielt|Er spielt Gitarre.|He plays the guitar.
trinken|to drink|trinke|trinkst|trinkt|Ich trinke Wasser.|I drink water.
kaufen|to buy|kaufe|kaufst|kauft|Sie kauft Brot.|She buys bread.
gehen|to go|gehe|gehst|geht|Ich gehe nach Hause.|I am going home.
fahren|to drive, to travel|fahre|fährst|fährt|Er fährt mit dem Bus.|He takes the bus.
essen|to eat|esse|isst|isst|Sie isst einen Apfel.|She eats an apple.
sehen|to see|sehe|siehst|sieht|Ich sehe den Bus.|I see the bus.
lesen|to read|lese|liest|liest|Du liest ein Buch.|You read a book.
sprechen|to speak|spreche|sprichst|spricht|Er spricht Englisch.|He speaks English.
schlafen|to sleep|schlafe|schläfst|schläft|Das Baby schläft.|The baby is sleeping.
nehmen|to take|nehme|nimmst|nimmt|Ich nehme einen Kaffee.|I'll have a coffee.
geben|to give|gebe|gibst|gibt|Sie gibt mir das Buch.|She gives me the book.
laufen|to run, to walk|laufe|läufst|läuft|Er läuft schnell.|He runs fast.
helfen|to help|helfe|hilfst|hilft|Er hilft mir.|He helps me.
wissen|to know (a fact)|weiß|weißt|weiß|Ich weiß es nicht.|I don't know.
kennen|to know (a person)|kenne|kennst|kennt|Ich kenne ihn.|I know him.
können|can, to be able to|kann|kannst|kann|Ich kann schwimmen.|I can swim.
müssen|must, to have to|muss|musst|muss|Ich muss arbeiten.|I have to work.
wollen|to want|will|willst|will|Er will schlafen.|He wants to sleep.
möchten|would like|möchte|möchtest|möchte|Ich möchte einen Tee.|I would like a tea.
mögen|to like|mag|magst|mag|Ich mag Kaffee.|I like coffee.
dürfen|may, to be allowed to|darf|darfst|darf|Ich darf nicht essen.|I am not allowed to eat.
tanzen|to dance|tanze|tanzt|tanzt|Sie tanzt gern.|She likes dancing.
singen|to sing|singe|singst|singt|Er singt laut.|He sings loudly.
schreiben|to write|schreibe|schreibst|schreibt|Ich schreibe eine E-Mail.|I am writing an email.
kochen|to cook|koche|kochst|kocht|Er kocht Suppe.|He is cooking soup.
warten|to wait|warte|wartest|wartet|Ich warte auf den Bus.|I am waiting for the bus.
brauchen|to need|brauche|brauchst|braucht|Ich brauche Hilfe.|I need help.
finden|to find, to think|finde|findest|findet|Ich finde das gut.|I think that's good.
fragen|to ask|frage|fragst|fragt|Er fragt den Lehrer.|He asks the teacher.
verstehen|to understand|verstehe|verstehst|versteht|Ich verstehe das nicht.|I don't understand that.
hören|to hear, to listen to|höre|hörst|hört|Sie hört Musik.|She is listening to music.
öffnen|to open|öffne|öffnest|öffnet|Er öffnet die Tür.|He opens the door.
aufstehen|to get up|stehe … auf|stehst … auf|steht … auf|Ich stehe um sieben auf.|I get up at seven.
anrufen|to call|rufe … an|rufst … an|ruft … an|Sie ruft ihre Mutter an.|She calls her mother.
einkaufen|to shop|kaufe … ein|kaufst … ein|kauft … ein|Wir kaufen heute ein.|We are shopping today.
"""

    const val WORD_ORDER = """
Heute / ich / lerne / Deutsch|Heute lerne ich Deutsch.|The verb is always in second place, even when something else comes first.|Today I learn German.
Ich / Deutsch / lerne|Ich lerne Deutsch.|Normal order: subject, verb, then the rest.|I learn German.
Morgen / wir / fahren / nach Berlin|Morgen fahren wir nach Berlin.|The verb stays in second place, so the subject moves behind it.|Tomorrow we are going to Berlin.
Am Montag / ich / habe / frei|Am Montag habe ich frei.|Time first? Then the verb, then the subject.|On Monday I have the day off.
Im Sommer / wir / fahren / ans Meer|Im Sommer fahren wir ans Meer.|Time first? Then the verb, then the subject.|In summer we go to the sea.
Nach dem Essen / ich / gehe / spazieren|Nach dem Essen gehe ich spazieren.|The verb is in second place after the whole time phrase.|After the meal I go for a walk.
Wir / gern / spielen / Fußball|Wir spielen gern Fußball.|"gern" comes after the verb, before the thing you like.|We like playing football.
Ich / nicht / verstehe / das|Ich verstehe das nicht.|"nicht" usually goes at the end when it negates the whole sentence.|I don't understand that.
Er / kein Auto / hat|Er hat kein Auto.|"kein" replaces "ein" and goes in front of the noun.|He doesn't have a car.
Ich / um sieben / stehe / auf|Ich stehe um sieben auf.|The separable prefix (auf) goes to the end of the sentence.|I get up at seven.
Sie / ihre Mutter / ruft / an|Sie ruft ihre Mutter an.|The separable prefix (an) goes to the end of the sentence.|She calls her mother.
Ich / Kaffee / möchte / trinken|Ich möchte Kaffee trinken.|With a modal verb: the modal is in second place, the other verb goes to the end.|I would like to drink coffee.
Du / schwimmen / kannst|Du kannst schwimmen.|With a modal verb: the modal is in second place, the other verb goes to the end.|You can swim.
Wir / heute / müssen / arbeiten|Wir müssen heute arbeiten.|With a modal verb: the modal is in second place, the other verb goes to the end.|We have to work today.
Heute / ich / muss / arbeiten|Heute muss ich arbeiten.|Modal verb in second place, even after "heute".|Today I have to work.
Ich / jeden Tag / Deutsch / lerne|Ich lerne jeden Tag Deutsch.|Time usually comes before the thing (the object).|I learn German every day.
Sie / heute / zu Hause / bleibt|Sie bleibt heute zu Hause.|Time usually comes before the place.|She is staying at home today.
Ich / dir / das Buch / gebe|Ich gebe dir das Buch.|Who gets it (dir) usually comes before what you give (das Buch).|I give you the book.
Kannst / du / mir / helfen|Kannst du mir helfen?|A yes/no question starts with the verb.|Can you help me?
Hast / du / Hunger|Hast du Hunger?|A yes/no question starts with the verb.|Are you hungry?
Möchtest / du / Tee / oder Kaffee|Möchtest du Tee oder Kaffee?|A yes/no question starts with the verb.|Would you like tea or coffee?
Wo / du / wohnst|Wo wohnst du?|Question word first, verb second.|Where do you live?
Was / du / machst / heute|Was machst du heute?|Question word first, verb second.|What are you doing today?
Wann / der Bus / kommt|Wann kommt der Bus?|Question word first, verb second.|When does the bus come?
Woher / Sie / kommen|Woher kommen Sie?|Question word first, verb second.|Where are you from?
Warum / du / lernst / Deutsch|Warum lernst du Deutsch?|Question word first, verb second.|Why do you learn German?
Ich bleibe zu Hause / weil / müde / ich / bin|Ich bleibe zu Hause, weil ich müde bin.|After "weil" the verb goes to the end.|I'm staying at home because I'm tired.
Ich lerne Deutsch / weil / arbeite / ich / in Berlin|Ich lerne Deutsch, weil ich in Berlin arbeite.|After "weil" the verb goes to the end.|I learn German because I work in Berlin.
Ich weiß / dass / kommt / er|Ich weiß, dass er kommt.|After "dass" the verb goes to the end.|I know that he is coming.
Ich bin müde / aber / arbeite / ich|Ich bin müde, aber ich arbeite.|"und", "aber", "oder" and "denn" do not change the word order.|I am tired, but I'm working.
Ich esse Brot / und / trinkt / er / Tee|Ich esse Brot und er trinkt Tee.|"und", "aber", "oder" and "denn" do not change the word order.|I eat bread and he drinks tea.
"""

    const val QUESTIONS = """
You ask someone's name (informal).|Wie heißt du?|"du" is for friends, family and children.|Ich heiße Anna.|My name is Anna.
You ask someone's name (formal).|Wie heißen Sie?|"Sie" is formal "you". The verb ends in -en.|Ich heiße Müller.|My name is Müller.
You ask where someone is from.|Woher kommst du?|"woher" means where from.|Ich komme aus Spanien.|I come from Spain.
You ask where someone lives.|Wo wohnst du?|"wo" means where, in one place.|Ich wohne in Hamburg.|I live in Hamburg.
You ask where someone is going.|Wohin gehst du?|"wohin" means where to.|Ich gehe nach Hause.|I'm going home.
You ask how someone is.|Wie geht es dir?|"dir" means "to you".|Mir geht es gut, danke.|I'm fine, thank you.
You ask how old someone is.|Wie alt bist du?|German uses "sein" (to be), not "haben".|Ich bin 25 Jahre alt.|I'm 25 years old.
You ask what someone does for work.|Was machst du beruflich?|You can also ask "Was bist du von Beruf?".|Ich bin Lehrerin.|I'm a teacher.
You ask what something is.|Was ist das?|"was" means what.|Das ist ein Hund.|That's a dog.
You ask who someone is.|Wer ist das?|"wer" means who.|Das ist mein Bruder.|That's my brother.
You ask what time it is.|Wie spät ist es?|"halb drei" means half past two.|Es ist halb drei.|It's half past two.
You ask when something starts.|Wann beginnt der Film?|"wann" means when.|Der Film beginnt um acht.|The film starts at eight.
You ask how much something costs.|Wie viel kostet das?|"wie viel" for amounts, "wie viele" for things you can count.|Das kostet fünf Euro.|That costs five euros.
You ask how many.|Wie viele Brüder hast du?|"viele" goes with plural nouns.|Ich habe zwei Brüder.|I have two brothers.
You ask why.|Warum lernst du Deutsch?|You answer with "weil", and the verb goes to the end.|Weil ich in Deutschland arbeite.|Because I work in Germany.
You ask which book someone wants.|Welches Buch möchtest du?|"welches" because Buch is "das". It changes with the noun.|Das rote.|The red one.
You ask how to say something in German.|Wie sagt man "thank you" auf Deutsch?|"man" means one, people in general.|Man sagt "Danke".|You say "Danke".
You did not hear what was said.|Wie bitte?|A polite way to say "pardon?".|Ich sage: Guten Morgen.|I say: Good morning.
You ask someone to say it again.|Können Sie das bitte wiederholen?|"können" is in second place, "wiederholen" goes to the end.|Ja, gern.|Yes, gladly.
You ask if someone speaks English.|Sprechen Sie Englisch?|A yes/no question starts with the verb.|Ja, ein bisschen.|Yes, a little.
You ask if someone has a pen.|Hast du einen Stift?|"einen" because Stift is "der" (accusative).|Ja, hier bitte.|Yes, here you go.
You ask if a seat is free.|Ist hier noch frei?|A short way to ask if a seat is free.|Ja, bitte.|Yes, please.
You ask whether you may sit down.|Darf ich mich setzen?|"dürfen" is for asking permission.|Ja, natürlich.|Yes, of course.
You ask where the station is.|Wo ist der Bahnhof?|"der Bahnhof" is masculine.|Der Bahnhof ist dort.|The station is over there.
You ask where the toilet is.|Wo ist die Toilette?|"die Toilette" is feminine.|Die Toilette ist links.|The toilet is on the left.
You ask what someone would like to drink.|Was möchten Sie trinken?|"möchten" is polite. The second verb goes to the end.|Ich möchte einen Tee, bitte.|I would like a tea, please.
You ask whether the food is good.|Schmeckt es dir?|"schmecken" means to taste (good).|Ja, sehr gut!|Yes, very good!
You ask what day it is.|Welcher Tag ist heute?|"welcher" because Tag is "der".|Heute ist Montag.|Today is Monday.
You ask what someone is doing at the weekend.|Was machst du am Wochenende?|"am" + a day or weekend: "am Montag", "am Wochenende".|Ich besuche meine Eltern.|I'm visiting my parents.
You ask where someone works.|Wo arbeitest du?|"arbeiten" adds an e: du arbeitest.|Ich arbeite in einem Büro.|I work in an office.
"""
}
