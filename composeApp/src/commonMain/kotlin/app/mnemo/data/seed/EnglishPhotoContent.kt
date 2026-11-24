package app.mnemo.data.seed

/**
 * Content of the English deck's "Name it (photos)" section ([EnglishDeck.photos]): real photos of
 * everyday things. One card per line, fields separated by `|`:
 *
 * photo key | the word | what it is | example | example
 *
 * The photo is `files/seed/en/<key>.webp`; its credit is in [EnglishPhotoCredits].
 */
internal object EnglishPhotoContent {
    const val KITCHEN = """
colander|a colander|a bowl with holes, for draining pasta or washing vegetables|Drain the pasta in a colander.|Rinse the berries in the colander.
whisk|a whisk|a tool of wire loops for beating eggs or cream|Beat the eggs with a whisk.|Whisk the cream until it's thick.
ladle|a ladle|a big deep spoon for serving soup|She served the soup with a ladle.|Pass me the ladle, please.
spatula|a spatula|a flat tool for scraping bowls or turning food in a pan|Scrape the bowl clean with a spatula.|Flip the pancake with a spatula.
tongs|tongs|a tool with two arms for picking up hot food|Turn the sausages with the tongs.|Use tongs, not your fingers!
chopping-board|a chopping board (US: a cutting board)|a board you cut food on|Put the onion on the chopping board.|Wash the chopping board after cutting raw meat.
grater|a grater|a metal tool with sharp holes for cheese or carrots|Grate some cheese with the grater.|Mind your fingers on the grater.
peeler|a peeler|a small tool that takes the skin off fruit and vegetables|Peel the potatoes with a peeler.|This peeler is really sharp.
kettle|a kettle|you boil water in it for tea|I'll put the kettle on.|The kettle has just boiled.
saucepan|a saucepan|a deep round pan with a long handle, for boiling or heating|Heat the milk in a small saucepan.|Don't let the saucepan boil dry.
oven-glove|an oven glove (US: an oven mitt)|a thick glove for taking hot dishes out of the oven|Use an oven glove, the tray is hot!|I burnt my hand because I forgot the oven glove.
tin-opener|a tin opener (US: a can opener)|a tool for opening tins (cans)|Where's the tin opener? I want some beans.|This tin opener is broken.
corkscrew|a corkscrew|a tool for pulling the cork out of a wine bottle|Have you got a corkscrew?|We couldn't open the wine without a corkscrew.
measuring-jug|a measuring jug (US: a measuring cup)|a jug with lines that show how much liquid is in it|Add 200 ml of milk using a measuring jug.|Fill the measuring jug to the half-litre line.
cling-film|cling film (US: plastic wrap)|thin clear plastic for covering food|Cover the bowl with cling film and put it in the fridge.|Wrap the sandwiches in cling film.
tin-foil|tin foil (aluminium foil)|thin metal sheet for wrapping or cooking food|Wrap the fish in foil and bake it.|Cover the tray with tin foil.
"""

    const val BATHROOM = """
nail-clippers|nail clippers|a small tool for cutting your nails|I need to borrow your nail clippers.|Nail clippers aren't allowed in some hand luggage.
tweezers|tweezers|a small tool for pulling out hairs or splinters|She pulled out the splinter with tweezers.|I use tweezers to shape my eyebrows.
hairdryer|a hairdryer|it dries your hair with hot air|Can I use your hairdryer?|The hotel room has a hairdryer.
comb|a comb|a thin strip with teeth for tidying your hair|Run a comb through your hair.|He always carries a comb in his pocket.
hairbrush|a hairbrush|a brush for your hair|My hairbrush is full of hair.|Brush your hair before you go out.
dental-floss|dental floss|thin thread for cleaning between your teeth|The dentist told me to use dental floss every day.|Floss your teeth before you brush them.
towel-rail|a towel rail (a heated towel rail)|a bar or frame where towels hang to dry|Hang your towel on the towel rail.|The heated towel rail keeps the towels warm.
shower-curtain|a shower curtain|a plastic curtain that keeps the water in the shower|Close the shower curtain or the floor gets wet.|The shower curtain needs washing.
cotton-buds|a cotton bud (US: a Q-tip, a cotton swab)|a short stick with cotton on both ends|Don't put cotton buds inside your ears.|She cleaned the keyboard with a cotton bud.
deodorant|deodorant|it stops you smelling of sweat|Don't forget to put on deodorant.|I've run out of deodorant.
toilet-brush|a toilet brush|a brush for cleaning the toilet bowl|The toilet brush is behind the toilet.|Clean the toilet with the toilet brush.
bathroom-scales|bathroom scales (US: a bathroom scale)|you stand on them to see your weight|I weighed myself on the bathroom scales.|These scales say I've put on two kilos!
hair-tie|a hair tie (a hair bobble)|an elastic band for tying your hair back|Have you got a spare hair tie?|She tied her hair back with a hair tie.
washcloth|a flannel (US: a washcloth)|a small square towel for washing your face|Wipe your face with a warm flannel.|Each guest gets a towel and a washcloth.
"""

    const val HOUSE = """
extension-lead|an extension lead (US: a power strip)|a cable with several sockets on the end|Plug the laptop into the extension lead.|We need a longer extension lead.
socket|a socket (US: an outlet)|where you plug things in on the wall|Is there a socket near the bed?|Don't overload the socket.
light-switch|a light switch|you press it to turn the light on or off|The light switch is by the door.|Can you flick the light switch?
radiator|a radiator|a metal heater on the wall, filled with hot water|Put your wet gloves on the radiator.|The radiator in my room isn't working.
doormat|a doormat|a mat outside or inside the front door for wiping your shoes|Wipe your feet on the doormat.|The key is under the doormat.
coat-hanger|a coat hanger (a hanger)|you hang clothes on it in a wardrobe|Hang your shirt on a coat hanger.|We need more hangers in the wardrobe.
clothes-peg|a clothes peg (US: a clothespin)|a small clip that holds washing on a line|Hang the sheets up with clothes pegs.|The wind blew the pegs off the line.
ironing-board|an ironing board|a narrow table for ironing clothes|Set up the ironing board in the kitchen.|The ironing board folds flat.
iron|an iron|a hot tool that takes the creases out of clothes|Is the iron on? I can smell burning.|Iron your shirt before the interview.
dustpan|a dustpan and brush|for sweeping up small bits of dirt|Sweep the crumbs into the dustpan.|Where's the dustpan and brush?
mop|a mop|a stick with a soft head for washing floors|Mop the kitchen floor.|Wring the mop out in the bucket.
vacuum-cleaner|a vacuum cleaner (a hoover)|a machine that sucks up dust|I need to vacuum the carpet.|The vacuum cleaner is really loud.
clothes-horse|a clothes horse (a drying rack)|a folding frame for drying washing indoors|Put the wet clothes on the clothes horse.|The clothes horse takes up half the room.
bin-bag|a bin bag (US: a garbage bag)|a big plastic bag that goes inside a bin|Take the bin bag out, please.|We've run out of bin bags.
duvet|a duvet (US: a comforter)|a thick soft cover you sleep under|It's cold; I need a thicker duvet.|Change the duvet cover once a week.
pillowcase|a pillowcase|the cloth cover of a pillow|The pillowcases match the duvet cover.|Put a clean pillowcase on.
blinds|blinds|a window covering of strips you can raise or turn|Close the blinds, the sun is in my eyes.|Pull up the blinds and let some light in.
doorbell|a doorbell|you press it so people inside know you're there|Somebody's ringing the doorbell.|The doorbell doesn't work, so knock.
smoke-alarm|a smoke alarm|it beeps loudly when it detects smoke|Test the smoke alarm every month.|The smoke alarm went off when I burnt the toast.
remote-control|a remote control (a remote)|you change TV channels with it|Where's the remote?|The remote control needs new batteries.
"""

    const val TOOLS = """
pliers|pliers|a tool for gripping, bending or cutting wire|Hold the nail with the pliers.|Use pliers to bend the wire.
tape-measure|a tape measure|a long measuring tape that rolls up|Measure the window with a tape measure.|The tape measure says 2.4 metres.
drill|a drill|a power tool that makes holes|Drill a hole in the wall for the shelf.|I borrowed a drill from my neighbour.
nails|a nail|a thin pointed piece of metal you hammer into wood|Hammer the nail into the wall.|I stepped on a nail!
screws|a screw|a metal pin with a spiral groove, turned with a screwdriver|Tighten the screws on the table leg.|This screw is loose.
toolbox|a toolbox|a box for keeping tools in|The hammer's in the toolbox.|He keeps a toolbox in the car.
paintbrush|a paintbrush|a brush for painting|Clean the paintbrush in water.|I need a smaller paintbrush for the corners.
paint-roller|a paint roller|a roller for painting walls quickly|Use a paint roller for the big walls.|Pour some paint into the tray for the roller.
sticky-tape|sticky tape (Sellotape; US: Scotch tape)|clear tape for sticking paper|Wrap the present and close it with sticky tape.|Have you got any sticky tape?
stapler|a stapler|it joins sheets of paper with a small metal clip|Staple the pages together.|The stapler has run out of staples.
scissors|scissors|a tool with two blades for cutting paper or cloth|Can I borrow your scissors?|Cut along the line with scissors.
glue-stick|a glue stick|solid glue in a tube, for paper|Stick the photo in with a glue stick.|The glue stick has dried out.
highlighter|a highlighter|a bright pen for marking important text|Highlight the key words.|She uses a yellow highlighter.
sticky-notes|a sticky note (a Post-it)|a small piece of paper that sticks and unsticks|I left a sticky note on your screen.|Her desk is covered in sticky notes.
eraser|a rubber (US: an eraser)|it removes pencil marks|Can I borrow your rubber?|Rub it out with an eraser.
pencil-sharpener|a pencil sharpener|it makes a pencil pointed again|My pencil needs sharpening; have you got a sharpener?|Empty the pencil sharpener in the bin.
rubber-band|a rubber band (an elastic band)|a thin elastic ring for holding things together|Put a rubber band around the cards.|The rubber band snapped.
spirit-level|a spirit level|a tool with a bubble that shows if something is straight|Check the shelf with a spirit level.|The picture isn't straight; get the spirit level.
"""

    const val STREET = """
zebra-crossing|a zebra crossing (a pedestrian crossing)|black and white stripes where people cross the road|Cross at the zebra crossing.|Cars must stop at a zebra crossing.
roundabout|a roundabout (US: a traffic circle)|a junction where cars drive around a circle|Take the second exit at the roundabout.|There's always traffic at that roundabout.
kerb|a kerb (US: a curb)|the edge between the pavement and the road|Wait at the kerb until the cars stop.|He parked too close to the kerb.
streetlamp|a streetlight (a lamppost)|a tall light at the side of the street|Meet me under the streetlight.|The streetlights come on at dusk.
bus-stop|a bus stop|where the bus stops for passengers|I'll meet you at the bus stop.|The bus stop has a shelter.
postbox|a postbox (US: a mailbox)|where you post letters|Can you post this in the postbox on your way?|British postboxes are red.
speed-bump|a speed bump (a speed hump)|a raised strip across the road that slows cars down|Slow down, there's a speed bump.|They put speed bumps outside the school.
parking-meter|a parking meter|you pay for parking at it|Put some coins in the parking meter.|The parking meter only takes cards.
bench|a bench|a long seat for several people, often in a park|Let's sit on that bench.|He was reading on a park bench.
manhole-cover|a manhole cover|a round metal lid in the road over a hole to the drains|Don't ride over the manhole cover when it's wet.|Workers lifted the manhole cover.
fire-hydrant|a fire hydrant|a pipe in the street where firefighters get water|Don't park in front of the fire hydrant.|Kids played in the water from a fire hydrant.
ticket-machine|a ticket machine|you buy tickets at it|Buy a ticket from the machine before you get on.|The ticket machine only takes cards.
playground|a playground|an outdoor area with swings and slides for children|The kids are at the playground.|There's a new playground in the park.
"""

    const val SHOP = """
checkout|a checkout (a till)|where you pay in a shop|There's a long queue at the checkout.|Use the self-service checkout.
card-reader|a card reader (a card machine)|where you pay by card or tap your phone|Just tap your card on the card reader.|The card machine isn't working; cash only.
vending-machine|a vending machine|a machine that sells drinks or snacks|I got a coffee from the vending machine.|The vending machine took my money!
barcode|a barcode|black lines on a product that a scanner reads|Scan the barcode.|The barcode won't scan.
bananas|a bunch of bananas|several bananas joined together|A bunch of bananas, please.|These bananas aren't ripe yet.
honey-jar|a jar of honey|a glass pot of honey|I put a spoonful from the honey jar in my tea.|The lid on the honey jar is stuck.
crisps|a packet of crisps (US: a bag of chips)|thin fried slices of potato in a bag|Would you like a packet of crisps?|Salt and vinegar is my favourite flavour of crisps.
"""

    const val CLOTHES = """
zip|a zip (US: a zipper)|two rows of teeth that close a jacket or bag|My zip is stuck.|Do your zip up, it's cold.
button|a button|a small round thing you push through a hole to close clothes|A button has come off my shirt.|Can you sew this button back on?
shoelace|a shoelace|a string for tying your shoe|Your shoelace is undone.|Tie your shoelaces or you'll trip.
collar|a collar|the part of a shirt or coat around your neck|Your collar is sticking up.|He loosened his collar and tie.
hood|a hood|the part of a coat that covers your head|Put your hood up, it's raining.|My coat has a hood with fur.
belt|a belt|a strip of leather round your waist that holds your trousers up|These trousers need a belt.|He tightened his belt.
pocket|a pocket|a small bag sewn into clothes|Put your phone in your pocket.|I found ten pounds in my coat pocket.
slippers|slippers|soft shoes you wear at home|Put your slippers on, the floor's cold.|I got some slippers for my birthday.
trainers|trainers (US: sneakers)|sports shoes|I need new trainers for running.|You can wear trainers to the office on Fridays.
hoodie|a hoodie|a sweatshirt with a hood|He always wears a black hoodie.|Grab a hoodie, it gets cold in the evening.
raincoat|a raincoat|a waterproof coat|Take a raincoat, it might rain.|My raincoat kept me dry.
umbrella|an umbrella|you hold it over your head in the rain|Don't forget your umbrella.|The wind blew my umbrella inside out.
wallet|a wallet|a small flat case for money and cards|I've lost my wallet.|He took a twenty out of his wallet.
beanie|a beanie (a woolly hat)|a warm knitted hat that fits close to your head|Wear a beanie, it's freezing.|She bought a red beanie for the winter.
"""
}
