package app.mnemo.data.seed

/**
 * Content of the English section "Keep the conversation going": what to say to keep a real
 * conversation natural. One card per line: situation | what you say | another way | another way.
 */
internal object EnglishTalkContent {
    const val REACTIONS = """
Your friend says they got the job.|That's great news — congratulations!|Well done, you deserve it!|Good for you!
Your friend says they failed their exam.|Oh no, I'm so sorry to hear that.|That's a shame. What happened?|That sounds really tough.
Someone tells you a surprising story.|No way! Are you serious?|Really? I had no idea!|Wow, that's unbelievable!
Someone describes a terrible trip.|That sounds awful.|Oh dear, what a nightmare!|You poor thing!
You agree with what someone says.|Exactly! That's what I think too.|You're absolutely right.|I couldn't agree more.
You only partly agree.|Yes, in a way — but not completely.|I see what you mean, up to a point.|That's partly true.
Someone tells you about their holiday plans.|That sounds amazing — I'm jealous!|Lucky you! Have a great time.|How exciting!
Someone makes a joke.|Ha, that's funny!|You're kidding!|Good one!
Someone explains something and now you understand.|Oh, I see! That makes sense.|Ah, now I get it.|Right, got it.
Someone tells you they're nervous.|Don't worry, you'll be fine.|I'm sure it'll go well.|Try not to stress about it.
Someone offers help and you'd like it.|That would be great, thank you!|Oh, that'd be really kind of you.|If you don't mind, yes please.
Someone gives you a compliment.|Thank you, that's really kind of you.|Oh, thanks, you're too kind!|Aw, thank you — that means a lot.
Someone says sorry for being late.|Don't worry about it, it's fine.|No problem at all.|Never mind, these things happen.
Someone suggests a plan and you're not sure.|Hmm, I'm not sure about that.|I'd have to think about it.|Let me get back to you on that.
Your friend loves something you don't care for.|Fair enough — whatever makes you happy!|Each to their own!|If you like it, that's what matters.
Someone tells you they've moved abroad.|Wow, that's a big change!|That must be exciting — and scary!|Good for you for taking the chance.
"""

    const val FOLLOW_UPS = """
A friend says they went on holiday.|Oh nice, where did you go?|How was it?|What was the best part?
A friend says they started a new job.|How's it going so far?|What do you do there exactly?|How are you finding it?
A friend says they're learning to cook.|How come you started?|What do you like to cook?|Have you made anything good yet?
Someone says they're tired.|How come? Didn't you sleep well?|Oh no, what's keeping you up?|Rough week?
Someone says they live in a small town.|What's it like there?|Do you like living there?|How long have you lived there?
Someone says they love a TV series.|What's it about?|Who's your favourite character?|Would you recommend it?
Someone says they're busy this weekend.|Oh, what are you up to?|Anything fun planned?|What have you got on?
Someone says they went to a concert.|Who did you see?|Was it any good?|How was the atmosphere?
Someone says they have a big exam.|When is it?|How are you preparing for it?|What's the exam on?
Someone says they're moving soon.|Where are you moving to?|When's the big day?|Are you looking forward to it?
You meet someone new at a party.|How do you know the host?|So what brings you here?|Have you been here before?
Someone mentions a hobby.|How did you get into that?|How long have you been doing it?|Is it hard to learn?
Someone says they come from another country.|What's it like there?|What do you miss most?|How long have you been here?
You want to start small talk with a new colleague.|So, what do you do for a living?|What do you do in your free time?|Do you have any plans for the summer?
You want more detail about something.|Could you tell me a bit more about that?|What do you mean exactly?|Can you give me an example?
You want to show you're listening.|Mm-hm, go on.|And then what happened?|Wait, so what did you say?
"""

    const val POLITE = """
You want to disagree politely.|I see your point, but I think…|I'm not sure I agree with that.|That's one way to look at it, but…
You want to say no to an invitation.|I'd love to, but I can't make it.|Thanks for asking, but I'm busy that day.|Sorry, I'm afraid I can't.
You want to ask for a favour.|Would you mind helping me with this?|I was wondering if you could give me a hand.|Could I ask you a favour?
Someone nearby is too loud.|Sorry, could you keep it down a bit?|Would you mind lowering your voice?|Excuse me, I'm trying to concentrate.
Someone has a fact wrong.|Actually, I think it was on Tuesday.|Sorry, I think you may have got that wrong.|I might be wrong, but I thought…
You give your opinion politely.|In my opinion, it's a good idea.|If you ask me, we should wait.|The way I see it, it's too risky.
You have bad news.|I'm afraid I have some bad news.|Unfortunately, it didn't work out.|I'm sorry to say that…
You want to give kind criticism.|It's good, but it could be a bit shorter.|I really like it — maybe we could change one thing.|One small suggestion, if you don't mind…
You don't want to sound too sure.|It seems to me that it's a bit expensive.|I'd say it's probably fine.|It looks like it might rain.
You can't do something today at work.|I'm afraid I won't be able to do that today.|I'd love to help, but I'm swamped.|Could we do it tomorrow instead?
You didn't hear what someone said.|Sorry, could you say that again?|I didn't quite catch that.|Sorry, what was that?
You want to interrupt politely.|Sorry to interrupt, but…|Can I just add something?|Excuse me, may I say something?
Someone keeps interrupting you.|Sorry, let me just finish this point.|If I could just finish…|Hold on, I'm nearly done.
You need something in a shop.|Excuse me, could you tell me where…?|Do you happen to have this in a larger size?|Would it be possible to…?
You don't know the answer.|I'm afraid I don't know.|I'm not sure, to be honest.|I have no idea, sorry.
Someone thanks you and you want to be modest.|Don't worry about it.|It's no trouble at all.|That's absolutely fine.
"""

    const val BUYING_TIME = """
You need a moment to think.|Let me think for a second.|That's a good question…|Hmm, give me a moment.
You can't find the right word.|How can I put this?|What's the word? It's on the tip of my tongue.|Sorry, my English is a bit rusty today.
You want to describe a word you forgot.|It's a thing you use to…|It's like a…, but smaller.|You know, the thing that…
You lost your train of thought.|Sorry, what was I saying?|Sorry, I lost my train of thought.|Where was I?
You want to say it in other words.|What I mean is…|Let me rephrase that.|In other words…
You said something wrong and want to fix it.|Sorry, I mean…|No, wait — I meant to say…|Let me correct that.
You want to check you understood.|So what you're saying is…|If I understand correctly, you mean…|Do you mean that…?
You are asked a tough question.|That's a tricky one.|I'd need to think about that.|I haven't really thought about it before.
You want to start your answer.|Well, let me see…|To be honest…|Okay, so…
You want to add a point.|Another thing is…|On top of that…|And I should also mention…
You want to get to the point.|The main thing is…|Basically, it comes down to this.|To cut a long story short…
You want to give an example.|For example…|Take my brother, for instance.|Let me give you an example.
You can't explain something well.|I'm not sure how to say this in English.|Sorry, I'm having trouble explaining this.|It's hard to explain, but…
Someone speaks too fast.|Could you speak a bit more slowly, please?|Sorry, you're talking a bit fast for me.|Could you slow down a little?
You need a minute on the phone.|Hold on a second, please.|Just a moment, let me check.|Bear with me for a second.
You want to end your answer.|So, that's about it.|Anyway, that's what I think.|That's pretty much all I wanted to say.
"""

    const val FILLERS = """
You want to change the subject.|By the way, did you hear about…?|Anyway, how's work going?|Oh, that reminds me…
You start telling a story.|So, I was thinking…|Okay, so here's the thing…|The thing is…
You want to say "in fact".|Actually, it was quite good.|As a matter of fact, I've been there.|To tell you the truth, I liked it.
You want to be honest.|To be honest, I'm not a fan.|Honestly, I'd rather stay home.|If I'm being honest…
You want to say "a bit" or "more or less".|It's kind of difficult.|It's sort of a mix of both.|It's a bit of a pain.
You want to check the other person follows.|You know what I mean?|Does that make sense?|See what I mean?
You want to sound relaxed and unsure.|I mean, it's fine, I guess.|I suppose so.|I guess it's okay.
You change your mind.|On second thought, let's go tomorrow.|Actually, never mind.|Come to think of it, that's better.
You want to finish a conversation.|Anyway, I should get going.|It was great catching up!|Let's keep in touch.
You return to what you were saying.|Anyway, as I was saying…|So, to go back to what I said…|Right, so then…
You have big news to share.|You won't believe what happened!|Guess what?|Get this —
You want to emphasise something.|Honestly, it was the best day ever.|I'm telling you, it was amazing!|Seriously, you have to try it.
You don't want to be exact.|Something like that.|Or something.|More or less.
You want to say "that's the point".|That's exactly the point.|That's what I'm saying.|Which is why…
"""
}
