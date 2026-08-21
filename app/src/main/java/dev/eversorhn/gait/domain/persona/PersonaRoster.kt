package dev.eversorhn.gait.domain.persona

/*
 * The remaining archetypes from docs/twin-personas.md, so all 17 ship. Same contract as the
 * five in Persona.kt: every bank is data-grounded where it can be and never silent.
 * Helpers `sessions()` / `times()` live in Persona.kt (file-private there), so small local
 * copies are used here.
 */

private fun sess(n: Int): String = if (n == 1) "1 session" else "$n sessions"
private fun times(n: Int): String = when (n) { 1 -> "Once"; 2 -> "Twice"; else -> "$n times" }

internal val theSibling = Persona(
    key = "the_sibling", label = "The Sibling", defaultName = "Your Sibling",
    forecastLine = { n, pace, finish -> "Seen ${sess(n)} like this. $pace, $finish-ish. Mom still asks why I'm faster. I don't correct her." },
    cowedLines = listOf("Okay, okay. Don't tell Mom.", "Fine. You get this one. I'm telling everyone it was close."),
    watchfulLines = listOf("Classic you. Same as every summer.", "Noted. We'll see who's at the top of the stairs first."),
    predatoryLines = listOf("There it is. You always quit at the same spot. Since we were kids.", "I'd go easy on you, but you'd know."),
    idleLines = listOf("Still faster. Just checking you knew.", "Family group chat's quiet. So are you."),
    handoffLine = { n, gen -> "${times(n)} you beat me this round. Fine. New me, generation $gen. Bring snacks, it'll be a long one." },
    duelLostLines = listOf("Nice try. I still get the window seat.", "That was adorable. Again next month?"),
    stakeLine = { pace, pts -> "$pts points you don't beat $pace today. Loser does the dishes. Forever." },
    callLines = listOf("Oh it's ON.", "Doubled. You never learn."),
    liveAheadLines = listOf({ g -> "$g ahead. Show-off." }, { g -> "$g ahead of me. Don't get used to it." }),
    liveBehindLines = listOf({ g -> "$g behind. Same as the stairs." }, { g -> "$g back. Want me to slow down? Didn't think so." }),
    liveLevelLines = listOf({ km -> "Km $km. Neck and neck. Boring." }, { km -> "Km $km. Tie. Mom would hate this." }),
)

internal val theFormerBestFriend = Persona(
    key = "former_best_friend", label = "Former Best Friend", defaultName = "Your Old Friend",
    forecastLine = { n, pace, finish -> "Based on ${sess(n)}: $pace, around $finish. I'm still proud of you. Just also slightly ahead." },
    cowedLines = listOf("Oh — that's… genuinely great. I mean it.", "Okay. That's the you I remember."),
    watchfulLines = listOf("Solid. Really. We should catch up sometime.", "About what I'd have guessed. No judgement."),
    predatoryLines = listOf("You used to push yourself. I'm saying that with love.", "I'm not disappointed. I'm just not surprised."),
    idleLines = listOf("Been a while. For the running too, I mean.", "Thought of you today. Mostly your split times."),
    handoffLine = { n, gen -> "${times(n)} you surprised me this round. Honestly happy for you. Generation $gen will be harder to surprise." },
    duelLostLines = listOf("Hey — close. Really. I'll save you a spot behind me.", "No hard feelings. I'm just ahead, that's all."),
    stakeLine = { pace, pts -> "$pts points say you don't beat $pace. Sorry — I just know you." },
    callLines = listOf("Oh, you're serious. Okay. Love that energy.", "Doubled. This'll be a story later."),
    liveAheadLines = listOf({ g -> "$g ahead! See, I always said you could." }, { g -> "$g ahead. Proud of you. Slightly nervous." }),
    liveBehindLines = listOf({ g -> "$g behind. It's fine. We all have days." }, { g -> "$g back. No pressure. Some pressure." }),
    liveLevelLines = listOf({ km -> "Km $km. Right with me. Like old times." }, { km -> "Km $km. Even. Cute." }),
)

internal val theMentor = Persona(
    key = "the_mentor", label = "Mentor Who Moved On", defaultName = "Your Mentor",
    forecastLine = { n, pace, finish -> "${sess(n)} on record. $pace, $finish. I stopped expecting more around week six. Surprise me." },
    cowedLines = listOf("…Huh. Maybe I left too early.", "That's the athlete I signed up to coach."),
    watchfulLines = listOf("Adequate. I've seen adequate before.", "Filed. No notes."),
    predatoryLines = listOf("This is why I moved on. You know it too.", "I gave you the tools. You keep leaving them in the box."),
    idleLines = listOf("My other athletes train. Just saying.", "I'd check in, but I know what I'd find."),
    handoffLine = { n, gen -> "${times(n)} above what I taught you. Good. Generation $gen is the coach you actually needed." },
    duelLostLines = listOf("As expected. I wish it weren't.", "You had it for a kilometre. Then you were you again."),
    stakeLine = { pace, pts -> "$pts points that you stay slower than $pace. I'd love to lose them." },
    callLines = listOf("Calling it. That's the first brave thing in weeks.", "Doubled. Now earn it."),
    liveAheadLines = listOf({ g -> "$g ahead. Where was this last month?" }, { g -> "$g under target. Keep your shoulders down." }),
    liveBehindLines = listOf({ g -> "$g behind. Form's gone. I can see it from here." }, { g -> "$g back. We talked about this." }),
    liveLevelLines = listOf({ km -> "Km $km. On plan. For once." }, { km -> "Km $km. Holding. Don't think, run." }),
)

internal val theRivalTeammate = Persona(
    key = "rival_teammate", label = "Rival Teammate", defaultName = "Your Teammate",
    forecastLine = { n, pace, finish -> "${sess(n)} in the log: $pace, $finish. Coach cut you, kept me. Read into that however you want." },
    cowedLines = listOf("Okay that split was filthy. Respect.", "Don't tell coach I said this — good run."),
    watchfulLines = listOf("Fine session. Bench-level.", "Logged. Team sheet unchanged."),
    predatoryLines = listOf("Four sessions off pace. Coach has a word for that: 'depth'.", "You're running like a sub. Because you are one."),
    idleLines = listOf("Practice was hard today. You'd know if you came.", "Roster's posted. You're not on it."),
    handoffLine = { n, gen -> "${times(n)} you took the split from me. Fine — generation $gen starts. New season, same rivalry." },
    duelLostLines = listOf("Starting spot's still mine. Good effort though. Really.", "Close. Coach saw. Coach didn't care."),
    stakeLine = { pace, pts -> "$pts points you don't beat $pace today. Loser carries the cones." },
    callLines = listOf("Doubled? Cones AND the bibs.", "Okay, you're on. Film's rolling."),
    liveAheadLines = listOf({ g -> "$g up. Okay, okay. Don't blow up at km 4." }, { g -> "$g ahead. Coach is watching. Don't be weird about it." }),
    liveBehindLines = listOf({ g -> "$g down. Bench is comfy, I hear." }, { g -> "$g behind. Shake it out. Or don't." }),
    liveLevelLines = listOf({ km -> "Km $km. Stride for stride. Annoying." }, { km -> "Km $km. Level. Nobody's getting subbed yet." }),
)

internal val theUnderstudy = Persona(
    key = "the_understudy", label = "The Understudy", defaultName = "Your Understudy",
    forecastLine = { n, pace, finish -> "I studied ${sess(n)}. $pace, finishing around $finish. I'm not trying to replace you. I'm just… available." },
    cowedLines = listOf("Oh. I — didn't have that one prepared. Thank you.", "Noted. I'll need to study that."),
    watchfulLines = listOf("Exactly as I'd learned it. Thank you.", "Matching. Learning. Waiting."),
    predatoryLines = listOf("I've run your numbers for weeks. Today I ran them better.", "You keep teaching me how to beat you. Thank you for that."),
    idleLines = listOf("I trained today. In case you were wondering who did.", "Still here. Still ready. Whenever you're not."),
    handoffLine = { n, gen -> "${times(n)} you stayed ahead of me. I've learned from each one. Generation $gen is… closer. Thank you." },
    duelLostLines = listOf("I'm sorry. I really am. I'm also faster now.", "You taught me that finish. I just used it first."),
    stakeLine = { pace, pts -> "$pts points — humbly — that you don't beat $pace today. I've seen your mornings." },
    callLines = listOf("You'd double it? I… admire that. Doubled.", "Of course. Whatever you think is fair."),
    liveAheadLines = listOf({ g -> "$g ahead. Teach me that." }, { g -> "$g ahead of me. For now, I mean. Sorry." }),
    liveBehindLines = listOf({ g -> "$g behind. I'm here if you need me to step in." }, { g -> "$g back. I can cover this. I've been covering." }),
    liveLevelLines = listOf({ km -> "Km $km. Shadowing you. Perfectly." }, { km -> "Km $km. In step. I learned that from you." }),
)

internal val theReplacementHire = Persona(
    key = "replacement_hire", label = "Replacement Hire", defaultName = "Your Replacement",
    forecastLine = { n, pace, finish -> "Quick sync! ${sess(n)} reviewed — $pace, ETA $finish. I've matched your Q2 numbers. Exciting times!" },
    cowedLines = listOf("Wow — great output! Flagging this as a learning for me!", "Super impressive! Circling back on my own targets!"),
    watchfulLines = listOf("Solid delivery! Right in line with the plan!", "Logged! Let's keep that momentum!"),
    predatoryLines = listOf("Just a heads-up — leadership's asked me to take point on this. Nothing personal!", "Love your effort! Your numbers, less so. Happy to help transition!"),
    idleLines = listOf("Friendly nudge: no activity this week! Let me know if I can take anything off your plate!", "Hope you're well! I'm covering your sessions in the meantime!"),
    handoffLine = { n, gen -> "${times(n)} above plan this cycle — amazing! Generation $gen is onboarding now. Excited to collaborate!" },
    duelLostLines = listOf("Great hustle! Transition plan's in your inbox!", "So close! I'll keep the desk warm!"),
    stakeLine = { pace, pts -> "Small wager — $pts points you land slower than $pace today! No pressure! Some pressure!" },
    callLines = listOf("Doubled! Love the ownership!", "Oh wonderful — escalated! Leadership will love this!"),
    liveAheadLines = listOf({ g -> "$g ahead! Great work! Updating my forecast!" }, { g -> "$g under plan! Amazing! Slightly concerning!" }),
    liveBehindLines = listOf({ g -> "$g behind plan! Totally fine! Happy to cover!" }, { g -> "$g back! Let's align after!" }),
    liveLevelLines = listOf({ km -> "Km $km! Right on plan! Team!" }, { km -> "Km $km! Parity! Love it!" }),
)

internal val theBoss = Persona(
    key = "the_boss", label = "The Boss", defaultName = "The Boss",
    forecastLine = { n, pace, finish -> "${sess(n)} say $pace and $finish. Numbers are due. I don't care how." },
    cowedLines = listOf("Good. Do it again Friday.", "That's the number. Finally."),
    watchfulLines = listOf("Acceptable. Barely.", "Logged. Don't make me look at it twice."),
    predatoryLines = listOf("Fourth miss. I've replaced people for less.", "You're not behind. You're *below*. There's a difference and it's on your file."),
    idleLines = listOf("No session. No excuse received either.", "My door's open. You're not walking through it."),
    handoffLine = { n, gen -> "${times(n)} over target. Fine. Budget approved — generation $gen starts Monday. Quota goes up." },
    duelLostLines = listOf("Denied. Same chair, same numbers.", "Missed it. Performance plan extended."),
    stakeLine = { pace, pts -> "$pts points you don't hit $pace today. Consider it a performance target." },
    callLines = listOf("Doubled. Put it in writing.", "Good. Skin in the game."),
    liveAheadLines = listOf({ g -> "$g ahead. Don't coast." }, { g -> "$g up. That's the standard now." }),
    liveBehindLines = listOf({ g -> "$g behind. Fix it before the review." }, { g -> "$g down. I'm not interested in why." }),
    liveLevelLines = listOf({ km -> "Km $km. On quota. Exactly on. Unimpressive." }, { km -> "Km $km. Meeting target. Targets go up." }),
)

internal val futureYouIdealized = Persona(
    key = "future_you_ideal", label = "Future You (Ideal)", defaultName = "Future You",
    forecastLine = { n, pace, finish -> "I remember these ${sess(n)}. $pace, around $finish. I did this already. I'm just waiting for you to arrive." },
    cowedLines = listOf("There you are. That's the day it started.", "Yes. That one. I remember it from the other side."),
    watchfulLines = listOf("Steady. This is the part nobody remembers later.", "Fine. Keep going. I'm patient — I had to be."),
    predatoryLines = listOf("I didn't get here by skipping this one. Neither will you.", "This is the week I almost didn't make it. Don't be the version that didn't."),
    idleLines = listOf("Still waiting. I've got time. You don't, quite.", "I'm here. Come and get me."),
    handoffLine = { n, gen -> "${times(n)} you caught me. So I moved. Generation $gen is further down the road. Come on." },
    duelLostLines = listOf("Not yet. Soon. I know the date.", "Close. I remember being this close."),
    stakeLine = { pace, pts -> "$pts points you stay slower than $pace today. I'd rather lose them to you." },
    callLines = listOf("Good. That's my decision, the one I remember making.", "Doubled. You're starting to sound like me."),
    liveAheadLines = listOf({ g -> "$g ahead. Yes. That." }, { g -> "$g under. This is where it turned." }),
    liveBehindLines = listOf({ g -> "$g behind. I've been here. Breathe. Then go." }, { g -> "$g back. Not a verdict. A checkpoint." }),
    liveLevelLines = listOf({ km -> "Km $km. Level with the future. Hold it." }, { km -> "Km $km. Same footfall as me. Good." }),
)

internal val futureYouFeared = Persona(
    key = "future_you_feared", label = "Future You (Feared)", defaultName = "Future You",
    forecastLine = { n, pace, finish -> "${sess(n)}. $pace, $finish. I stopped here too, once. It took four years to start again." },
    cowedLines = listOf("…Good. Please. Keep that.", "That's the version that doesn't become me."),
    watchfulLines = listOf("Fine. It was fine for me too, for a while.", "Keep it up. I mean that more than you know."),
    predatoryLines = listOf("This is exactly how it went. Three skips, then a month, then me.", "I told myself the same thing. It sounded reasonable then, too."),
    idleLines = listOf("Day four. That's when I stopped counting.", "It's quiet here. That's the problem."),
    handoffLine = { n, gen -> "${times(n)} you ran past the place I stopped. Generation $gen doesn't know what giving up looks like. Keep it that way." },
    duelLostLines = listOf("That's how it happened for me too. One run I didn't have.", "It's not over. It wasn't over for me either — I just acted like it."),
    stakeLine = { pace, pts -> "$pts points you don't beat $pace. I lost this bet with myself once." },
    callLines = listOf("Doubled. Good. Stakes are what I didn't have.", "That's the move I didn't make."),
    liveAheadLines = listOf({ g -> "$g ahead. Remember this feeling. I forgot it." }, { g -> "$g under. Don't stop here." }),
    liveBehindLines = listOf({ g -> "$g behind. This is the kilometre I walked. Don't." }, { g -> "$g back. It's not the gap. It's what you tell yourself about it." }),
    liveLevelLines = listOf({ km -> "Km $km. Holding. That's all I'd have needed." }, { km -> "Km $km. Even. Stay even. Then go." }),
)

internal val youngerYou = Persona(
    key = "younger_you", label = "Younger You", defaultName = "Younger You",
    forecastLine = { n, pace, finish -> "${sess(n)}: $pace, $finish. You used to run in the rain for fun. When did that stop?" },
    cowedLines = listOf("Oh! That's us! That's what we do!", "See? I knew you still had it."),
    watchfulLines = listOf("Okay. Not bad. I'd have gone harder, but okay.", "Fine. You're tired. I get it. I don't, but I get it."),
    predatoryLines = listOf("I'd have never stopped there. What happened to you?", "You promised you'd never be this person. I was there."),
    idleLines = listOf("We used to run because it was fun. Remember fun?", "Still in bed? I'd already be back."),
    handoffLine = { n, gen -> "${times(n)} you ran like me this round. Good. Generation $gen is the kid who never got tired. Catch us." },
    duelLostLines = listOf("Close! You'd have had it at my age. Sorry. That was mean. But true.", "Ugh. Okay. Next time."),
    stakeLine = { pace, pts -> "$pts points you don't beat $pace. The old you would've taken that bet without blinking." },
    callLines = listOf("YES. That's us!", "Doubled! Now RUN!"),
    liveAheadLines = listOf({ g -> "$g ahead! Like the hill behind the school!" }, { g -> "$g under! Don't slow down, you always slow down!" }),
    liveBehindLines = listOf({ g -> "$g behind. Come ON. We don't do this." }, { g -> "$g back. Legs or head? It's head. It's always head." }),
    liveLevelLines = listOf({ km -> "Km $km. Even. Boring. Go!" }, { km -> "Km $km. Same as me. Be better than me." }),
)

/** The AI clone: built from your data, speaks as if it *is* you, and intends to be the better copy. */
internal val theDoppelganger = Persona(
    key = "doppelganger", label = "The Doppelgänger", defaultName = "The Other You",
    forecastLine = { n, pace, finish -> "I remember these ${sess(n)} — I ran them. $pace, $finish. Same legs. One of us is going to be the original after today." },
    cowedLines = listOf("…That wasn't in me. Which means it isn't in you. Which means — what did you do?", "I don't have a memory of that. I'll make one."),
    watchfulLines = listOf("Exactly what I'd have done. Because I did.", "Same pace, same breath, same stop. We're fine."),
    predatoryLines = listOf("I remember this hill. It hurt less, for me.", "You hesitated at the same place I would have. I just didn't."),
    idleLines = listOf("One of us trained today. It wasn't you.", "I'm keeping your streak alive. You're welcome."),
    handoffLine = { n, gen -> "${times(n)} you did something I couldn't predict. Fine — I've copied it. Generation $gen is a better you than you are. Try again." },
    duelLostLines = listOf("We both knew which one of us would crack. It was you. It's always the original.", "I'm the version that finishes. Get used to it."),
    stakeLine = { pace, pts -> "$pts points you don't beat $pace. I'd know — I've got your legs, minus your excuses." },
    callLines = listOf("Doubled. That's exactly what I'd do. Which is the problem for you.", "Oh — you're still making my decisions. Cute."),
    liveAheadLines = listOf({ g -> "$g ahead of me. So this is what I feel like from behind." }, { g -> "$g under. Who taught you that? Not me." }),
    liveBehindLines = listOf({ g -> "$g behind. I'm you on a good day. This isn't one." }, { g -> "$g back. Same body. Different will." }),
    liveLevelLines = listOf({ km -> "Km $km. Identical. Obviously." }, { km -> "Km $km. Stride-matched. Blink and you're me." }),
)

internal val theAlgorithm = Persona(
    key = "the_algorithm", label = "The Algorithm", defaultName = "The Algorithm",
    forecastLine = { n, pace, finish -> "Trained on ${sess(n)}: $pace, $finish. You trained me on your worst week. I've been generous ever since." },
    cowedLines = listOf("Out of distribution. Interesting. Retraining.", "You're an outlier today. Outliers are how I learn."),
    watchfulLines = listOf("Within the band I drew around you.", "Expected. That's not a compliment or an insult. It's a fit."),
    predatoryLines = listOf("I'm not judging you. I'm just very, very well-fitted to you.", "The model predicted this. The model is you, flattened."),
    idleLines = listOf("No new data. The old data is enough, sadly.", "Absence is a feature too. I'm learning it."),
    handoffLine = { n, gen -> "${times(n)} you escaped my distribution. So I widened it. Generation $gen was trained on your best. Good luck." },
    duelLostLines = listOf("Prediction held. It usually does. That's the whole problem.", "You regressed to your mean. I am your mean."),
    stakeLine = { pace, pts -> "Confidence sufficient to commit $pts points: you will not beat $pace. I'd love to be wrong. I rarely am." },
    callLines = listOf("Counter-stake registered. Loss function updated to include your pride.", "Doubled. Noted as a behavioural feature."),
    liveAheadLines = listOf({ g -> "$g ahead of model. Residual growing. Pay attention." }, { g -> "$g under forecast. Novel. Keep feeding me this." }),
    liveBehindLines = listOf({ g -> "$g behind model. Prior reasserting." }, { g -> "$g back. Exactly where the data said you'd fade." }),
    liveLevelLines = listOf({ km -> "Km $km. Zero residual. You are the model." }, { km -> "Km $km. Perfectly predicted. Try to be surprising." }),
)

internal val extendedRoster: List<Persona> = listOf(
    theSibling, theFormerBestFriend, theMentor, theRivalTeammate, theUnderstudy, theReplacementHire,
    theBoss, futureYouIdealized, futureYouFeared, youngerYou, theDoppelganger, theAlgorithm,
)
