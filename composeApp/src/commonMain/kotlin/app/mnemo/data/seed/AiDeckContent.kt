package app.mnemo.data.seed

/**
 * Content of the built-in "AI & ML" deck ([AiDeck]). One card per line, fields separated by `|`.
 * A last field starting with `#` is the card's topic on the concept map ([AiDeck.topics]).
 * In [CODE], `\n` inside a field is a line break.
 */
internal object AiDeckContent {
    /** event | when | what happened and why it matters | #topic */
    const val HISTORY = """
McCulloch and Pitts' artificial neuron|1943|The first mathematical model of a neuron: weighted inputs, a threshold, an on/off output. Every artificial neural network descends from it.|#nn
Turing's "Computing Machinery and Intelligence"|1950|Turing replaced "Can machines think?" with the imitation game (the Turing test): if you can't tell the machine's answers from a person's, call it intelligent.
The Dartmouth workshop|1956|McCarthy, Minsky, Rochester and Shannon's summer research project. Its 1955 proposal coined the name "artificial intelligence"; it counts as the birth of AI as a field.
The Logic Theorist|1956|Newell, Simon and Shaw's program proved theorems from Principia Mathematica. Often called the first AI program: reasoning as search through possible steps.
Rosenblatt's perceptron|1958|A single-layer neural network that learned to classify simple pictures from examples, built as the Mark I Perceptron machine. The press promised machines that would soon walk and talk.|#nn
LISP|1958|John McCarthy's programming language, built on symbols and lists. It was the main language of AI research for about 30 years.
Arthur Samuel's checkers program|1959|A checkers player that got better by playing against itself. Samuel's 1959 paper made the term "machine learning" popular.|#rl
ELIZA|1966|Joseph Weizenbaum's chatbot played a therapist by matching patterns and turning your words into questions. People confided in it anyway: the "ELIZA effect".
Shakey the robot|1966–1972|SRI's mobile robot was the first to plan its own actions. The project gave us the A* search algorithm and the STRIPS planner.
Minsky and Papert's book "Perceptrons"|1969|It proved that a single-layer perceptron cannot learn functions such as XOR. Funding for neural networks dried up for more than a decade.|#nn
The Lighthill Report and the first AI winter|1973|A report for the UK government said AI had not delivered what it promised. Funding was cut in the UK and the US: the first "AI winter" of the mid-1970s.
Expert systems in business (XCON)|1980|Rule-based systems captured specialists' knowledge as if-then rules. DEC's XCON configured computer orders and saved millions a year: AI's first big commercial success.
Japan's Fifth Generation Computer project|1982|A ten-year national programme to build logic-programming AI computers. It fell short, but it pushed the US and UK into funding AI again.
Hopfield networks|1982|John Hopfield's recurrent network stores memories as stable states of an energy function. It made physicists and computer scientists take neural networks seriously again.|#nn
Backpropagation made popular|1986|Rumelhart, Hinton and Williams showed in Nature how to train networks with hidden layers: send the error backwards with the chain rule. It solved the XOR problem that had stopped perceptrons.|#calc
The second AI winter|1987|The market for special LISP machines collapsed and expert systems proved costly to keep up to date. Funding and interest fell again into the early 1990s.
LeNet reads handwritten digits|1989|Yann LeCun's convolutional network read ZIP codes on US post. Later versions read a large share of the cheques written in the US.|#cnn
TD-Gammon|1992|Gerald Tesauro's backgammon program learned by playing itself with temporal-difference learning and reached close to world-champion level.|#rl
Support vector machines take off|1995|Cortes and Vapnik's soft-margin SVM: find the boundary with the widest margin. With kernels, SVMs led many benchmarks until deep learning.|#classic
Long short-term memory (LSTM)|1997|Hochreiter and Schmidhuber added gates to recurrent networks so they can keep information over long sequences without the gradient vanishing.|#seq
Deep Blue beats Kasparov|1997|IBM's chess computer beat the world champion 3½–2½ in a six-game match, with brute-force search and hand-tuned evaluation, not learning.
Deep belief networks and "deep learning"|2006|Hinton, Osindero and Teh trained deep networks one layer at a time. The start of the name "deep learning" and of neural networks' comeback.|#nn
The ImageNet dataset|2009|Fei-Fei Li's team labelled over 14 million images in about 20,000 categories. Its yearly challenge (2010–2017) became the benchmark for computer vision.|#data
IBM Watson wins Jeopardy!|2011|Watson beat champions Ken Jennings and Brad Rutter at the TV quiz, combining search, language processing and answer ranking.
AlexNet|2012|Krizhevsky, Sutskever and Hinton's convolutional network, trained on two GPUs, won ImageNet with a top-5 error of 15.3% against 26.2% for the next team. The moment deep learning took over.|#cnn
word2vec|2013|Mikolov and colleagues at Google learned word vectors where meaning becomes geometry: king − man + woman ≈ queen.|#embed
DeepMind's Atari player (DQN)|2013|One deep Q-network learned to play dozens of Atari games from the raw pixels and the score alone.|#rl
Generative adversarial networks (GANs)|2014|Ian Goodfellow's idea: a generator makes fakes, a discriminator tries to spot them, and both improve. The first very realistic generated faces.|#gen
Neural machine translation and attention|2014|Sequence-to-sequence networks (Sutskever and colleagues) and attention (Bahdanau, Cho and Bengio) translated whole sentences. Google Translate switched to a neural system in 2016.|#seq
ResNet|2015|He and colleagues' residual connections made networks of 152 layers trainable. It won ImageNet with an error below the roughly 5% estimated for humans.|#cnn
AlphaGo beats Lee Sedol|2016|DeepMind's Go program won 4–1 against one of the best players in the world, combining deep networks with tree search. Its "move 37" in game 2 surprised every expert.|#rl
The Transformer|2017|"Attention Is All You Need" replaced recurrence with self-attention, so whole sequences are processed in parallel. Almost every modern language model is a Transformer.|#transformer
BERT and the first GPT|2018|Pre-train a Transformer on huge amounts of plain text, then fine-tune it: OpenAI's GPT (a decoder) and Google's BERT (an encoder) made this the standard recipe.|#llmtrain
GPT-2's staged release|2019|OpenAI first held back its 1.5-billion-parameter model over misuse fears and released it in stages: the start of the debate about releasing powerful models.|#llmtrain
GPT-3|2020|175 billion parameters. It could do new tasks from a few examples in the prompt, without any training: "few-shot" or in-context learning.|#llmuse
AlphaFold 2|2020|DeepMind's model predicted the 3D shape of proteins almost as accurately as lab experiments. Hassabis and Jumper shared the 2024 Nobel Prize in Chemistry for it.
Text-to-image goes mainstream|2021|OpenAI's CLIP and DALL·E linked pictures and text (January 2021); Stable Diffusion was released openly in 2022.|#gen
ChatGPT|2022|Released on 30 November 2022: a chat interface on a GPT-3.5 model tuned with human feedback (RLHF). It reached an estimated 100 million users in about two months.|#llmtrain
GPT-4 and open-weight models|2023|GPT-4 (March 2023) could read images and passed many professional exams; Meta's LLaMA (February 2023) started the wave of open-weight models.|#llmuse
Nobel Prizes for AI|2024|Physics: John Hopfield and Geoffrey Hinton, for neural networks. Chemistry: Demis Hassabis and John Jumper (AlphaFold), with David Baker.
The EU AI Act|2024|The first broad law on AI. Rules depend on the risk: some uses are banned (such as social scoring), high-risk systems need checks, chatbots must say they are AI. In force since August 2024.
Reasoning models|2024|Models such as OpenAI's o1 (September 2024) are trained to think step by step before they answer, spending more compute at answer time for better maths and code.|#llmuse
"""

    /** the events, out of order | the right order */
    const val ORDER = """
Put in order: AlexNet · Deep Blue beats Kasparov · the Transformer · the Dartmouth workshop|the Dartmouth workshop (1956) → Deep Blue (1997) → AlexNet (2012) → the Transformer (2017)
Put in order: backpropagation made popular · the perceptron · "Perceptrons" (the book) · LSTM|the perceptron (1958) → "Perceptrons" (1969) → backpropagation (1986) → LSTM (1997)
Put in order: ChatGPT · GPT-3 · BERT · word2vec|word2vec (2013) → BERT (2018) → GPT-3 (2020) → ChatGPT (2022)
Put in order: the first AI winter · expert systems boom · the second AI winter · the Lighthill Report|the Lighthill Report (1973) → first AI winter (mid-1970s) → expert systems boom (1980s) → second AI winter (1987)
Put in order: AlphaGo · ImageNet dataset · Watson wins Jeopardy! · GANs|ImageNet dataset (2009) → Watson (2011) → GANs (2014) → AlphaGo (2016)
Put in order: ELIZA · the Turing test · LISP · Shakey the robot|the Turing test (1950) → LISP (1958) → ELIZA (1966) → Shakey (1966–1972)
"""

    /** person | known for */
    const val PEOPLE = """
Ada Lovelace|Wrote the first published program (1843), for Babbage's Analytical Engine, and the "Lovelace objection": a machine can only do what we tell it to.
Alan Turing|The Turing machine (1936), codebreaking at Bletchley Park, and the Turing test (1950). Computing's top prize, the Turing Award, is named after him.
Donald Hebb|"Cells that fire together wire together" (1949): learning by strengthening the connections that are used together. The idea behind many learning rules.
Claude Shannon|Founded information theory (1948): bits and entropy. Co-organised the Dartmouth workshop and built a maze-learning mechanical mouse.
John McCarthy|Coined "artificial intelligence" in the Dartmouth proposal (1955) and invented LISP.
Marvin Minsky|Co-founded the MIT AI Lab, co-wrote "Perceptrons" (1969) with Seymour Papert and wrote "The Society of Mind".
Frank Rosenblatt|Invented the perceptron (1958), the first neural network that learned from examples.
Herbert Simon and Allen Newell|The Logic Theorist and the General Problem Solver: AI as search. Simon also won the Nobel Prize in economics for "bounded rationality".
Joseph Weizenbaum|Built ELIZA (1966), then became a critic of handing human judgement to computers ("Computer Power and Human Reason").
Judea Pearl|Bayesian networks and the mathematics of cause and effect (the do-calculus). Turing Award 2011; wrote "The Book of Why".
Vladimir Vapnik|Statistical learning theory (VC dimension) and support vector machines.
Geoffrey Hinton|Backpropagation (1986), Boltzmann machines, deep belief nets, and AlexNet with his students. Turing Award 2018 and Nobel Prize in Physics 2024. Left Google in 2023 to speak freely about AI risks.
Yann LeCun|Convolutional networks (LeNet) that read cheques and post codes. Turing Award 2018; long-time head of AI research at Meta (FAIR).
Yoshua Bengio|Neural language models (2003) and much of the early attention work, from his lab in Montréal (Mila). Turing Award 2018; a leading voice on AI safety.
Jürgen Schmidhuber|LSTM with Sepp Hochreiter (1997), and early work on meta-learning and artificial curiosity, at the IDSIA lab in Switzerland.
Richard Sutton|Father of modern reinforcement learning: temporal-difference learning, the textbook with Andrew Barto, and the essay "The Bitter Lesson". Turing Award with Barto (2024).
Fei-Fei Li|Created ImageNet, the dataset behind the deep-learning boom in vision. Co-directs Stanford's Human-Centered AI Institute.
Andrew Ng|Co-founded Google Brain and Coursera; his online Machine Learning course taught millions. Later led AI at Baidu.
Demis Hassabis|Co-founder and CEO of DeepMind: Atari DQN, AlphaGo, AlphaFold. Nobel Prize in Chemistry 2024.
Ian Goodfellow|Invented GANs (2014), co-wrote the "Deep Learning" textbook and helped start research on adversarial examples.
Ilya Sutskever|AlexNet co-author and sequence-to-sequence learning; co-founded OpenAI and was its chief scientist; founded Safe Superintelligence in 2024.
Ashish Vaswani|First author of "Attention Is All You Need" (2017), the paper that introduced the Transformer, written by eight researchers at Google.
Andrej Karpathy|Stanford's CS231n course, led AI for Tesla Autopilot, founding member of OpenAI. Known for teaching videos that build GPT from scratch; coined "vibe coding" (2025).
Timnit Gebru and Joy Buolamwini|"Gender Shades" (2018): commercial face analysis was far less accurate for darker-skinned women. Gebru later co-wrote "On the Dangers of Stochastic Parrots".
"""

    /** paper | authors, year | the idea it introduced | #topic */
    const val PAPERS = """
"A Logical Calculus of the Ideas Immanent in Nervous Activity"|McCulloch & Pitts, 1943|A neuron as a threshold logic unit: networks of them can compute logical functions.|#nn
"Learning representations by back-propagating errors"|Rumelhart, Hinton & Williams, 1986|Backpropagation: use the chain rule to send the error backwards and train hidden layers.|#calc
"Long Short-Term Memory"|Hochreiter & Schmidhuber, 1997|A memory cell with gates (input, forget, output) that keeps gradients from vanishing over long sequences.|#seq
"Gradient-Based Learning Applied to Document Recognition"|LeCun, Bottou, Bengio & Haffner, 1998|LeNet-5: convolution and pooling layers trained end to end, plus the MNIST digit dataset.|#cnn
"Random Forests"|Breiman, 2001|Many decision trees, each grown on a bootstrap sample with random feature subsets; average their votes.|#classic
"A Neural Probabilistic Language Model"|Bengio et al., 2003|Learn a vector (embedding) for each word together with a neural network that predicts the next word.|#embed
"ImageNet Classification with Deep Convolutional Neural Networks"|Krizhevsky, Sutskever & Hinton, 2012|AlexNet: a deep CNN trained on GPUs with ReLU, dropout and data augmentation.|#cnn
"Efficient Estimation of Word Representations in Vector Space"|Mikolov et al., 2013|word2vec: skip-gram and CBOW learn word vectors from the words around them, cheaply and at scale.|#embed
"Auto-Encoding Variational Bayes"|Kingma & Welling, 2013|The variational autoencoder (VAE) and the reparameterisation trick: z = μ + σ·ε lets gradients flow through sampling.|#gen
"Playing Atari with Deep Reinforcement Learning"|Mnih et al., 2013|Deep Q-networks (DQN): a CNN estimates action values from pixels, stabilised with experience replay.|#rl
"Generative Adversarial Nets"|Goodfellow et al., 2014|A generator and a discriminator play a game: one makes fakes, the other learns to tell them apart.|#gen
"Dropout: A Simple Way to Prevent Neural Networks from Overfitting"|Srivastava, Hinton et al., 2014|Randomly switch off units during training so the network can't rely on any one of them.|#regular
"Sequence to Sequence Learning with Neural Networks"|Sutskever, Vinyals & Le, 2014|An encoder LSTM reads the input sentence into a vector; a decoder LSTM writes the output from it.|#seq
"Neural Machine Translation by Jointly Learning to Align and Translate"|Bahdanau, Cho & Bengio, 2014|Attention: at each output word the decoder looks back at all input positions instead of one fixed vector.|#transformer
"Adam: A Method for Stochastic Optimization"|Kingma & Ba, 2014|Per-parameter step sizes from running averages of the gradient and of its square.|#optim
"Batch Normalization"|Ioffe & Szegedy, 2015|Normalise each layer's inputs over the mini-batch: faster, more stable training with higher learning rates.|#optim
"Deep Residual Learning for Image Recognition"|He, Zhang, Ren & Sun, 2015|ResNet: skip connections learn F(x) + x, so very deep networks train.|#cnn
"U-Net"|Ronneberger, Fischer & Brox, 2015|An encoder–decoder with skip connections for pixel-level segmentation; later the backbone of diffusion models.|#cnn
"Mastering the game of Go with deep neural networks and tree search"|Silver et al., 2016|AlphaGo: policy and value networks guide Monte Carlo tree search.|#rl
"Attention Is All You Need"|Vaswani et al., 2017|The Transformer: self-attention and feed-forward layers only, no recurrence, so training runs in parallel.|#transformer
"Proximal Policy Optimization Algorithms"|Schulman et al., 2017|PPO: limit (clip) how much the policy changes per update. Simple and stable; later used for RLHF.|#rl
"BERT: Pre-training of Deep Bidirectional Transformers"|Devlin et al., 2018|An encoder pre-trained by filling in masked words, then fine-tuned for each task.|#llmtrain
"Improving Language Understanding by Generative Pre-Training"|Radford et al., 2018|GPT-1: pre-train a decoder to predict the next word, then fine-tune it.|#llmtrain
"Language Models are Few-Shot Learners"|Brown et al., 2020|GPT-3 (175B parameters) learns new tasks from examples in the prompt: in-context learning.|#llmuse
"Scaling Laws for Neural Language Models"|Kaplan et al., 2020|Loss falls smoothly, as a power law, with more parameters, data and compute.|#llmtrain
"Denoising Diffusion Probabilistic Models"|Ho, Jain & Abbeel, 2020|Generate images by learning to undo noise added step by step.|#gen
"Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks"|Lewis et al., 2020|RAG: retrieve relevant documents and give them to the generator as context.|#embed
"An Image is Worth 16x16 Words"|Dosovitskiy et al., 2020|The Vision Transformer (ViT): cut an image into patches and treat them like words.|#transformer
"Learning Transferable Visual Models From Natural Language Supervision"|Radford et al., 2021|CLIP: train image and text encoders so matching pairs end up close together (contrastive learning).|#embed
"LoRA: Low-Rank Adaptation of Large Language Models"|Hu et al., 2021|Freeze the model and train small low-rank matrices added to its weights: a fraction of the parameters to fine-tune.|#llmtrain
"Training language models to follow instructions with human feedback"|Ouyang et al., 2022|InstructGPT: supervised fine-tuning, then a reward model from human rankings, then PPO (RLHF).|#llmtrain
"Training Compute-Optimal Large Language Models"|Hoffmann et al., 2022|Chinchilla: for a fixed budget, train on about 20 tokens per parameter; earlier big models were undertrained.|#llmtrain
"Chain-of-Thought Prompting Elicits Reasoning in Large Language Models"|Wei et al., 2022|Showing worked, step-by-step examples in the prompt makes large models much better at reasoning.|#llmuse
"FlashAttention"|Dao et al., 2022|Exact attention computed in tiles that fit in the GPU's fast memory: much faster and less memory.|#transformer
"High-Resolution Image Synthesis with Latent Diffusion Models"|Rombach et al., 2022|Run diffusion in a compressed latent space instead of on pixels: the basis of Stable Diffusion.|#gen
"Constitutional AI: Harmlessness from AI Feedback"|Bai et al. (Anthropic), 2022|Train a harmless assistant with feedback from an AI that follows a written list of principles.|#llmtrain
"Direct Preference Optimization"|Rafailov et al., 2023|DPO: learn from preference pairs directly with a simple loss, with no reward model and no RL.|#llmtrain
"""

    /** case | what happened | the lesson */
    const val FAILURES = """
Microsoft's Tay chatbot (2016)|A Twitter bot that learned from the people talking to it. Within about 16 hours trolls had taught it to post racist and offensive tweets, and it was shut down.|A system that learns from the public needs protection against coordinated abuse.
COMPAS risk scores (2016)|ProPublica found that this US tool for predicting reoffending wrongly labelled Black defendants as high risk nearly twice as often as white defendants. Its maker answered that the scores were equally calibrated for both groups.|Fairness has several definitions that can't all be met at once; you have to choose one and justify it.
Amazon's CV-screening model (reported 2018)|Trained on ten years of mostly male applications, it learned to mark down CVs containing the word "women's" (as in "women's chess club"). Amazon dropped it.|Historical data carries historical bias; a model learns it unless you check.
Google Photos labels people as gorillas (2015)|The app labelled photos of Black people as gorillas. Google's fix was to remove the "gorilla" label altogether.|Test on diverse data; a quick patch can hide the problem without fixing it.
"Gender Shades" (2018)|Commercial gender classifiers were wrong up to about 35% of the time for darker-skinned women and under 1% for lighter-skinned men.|Report accuracy for each group, not just the average.
Uber self-driving car crash, Tempe (2018)|A test car killed Elaine Herzberg, who was walking her bike across the road. The software kept changing its mind about what she was, emergency braking was switched off, and the safety driver was watching a video.|People are bad at watching over automation; safety has to be designed into the whole system.
Adversarial examples (2013–2014)|Changes to an image too small for people to see made networks confidently wrong: a panda classified as a gibbon.|A model can be accurate and still brittle; test robustness, not only accuracy.
Stickers on a stop sign (2018)|Researchers put a few black and white stickers on a stop sign and a classifier read it as a 45 mph speed-limit sign.|Attacks work in the physical world too, not only on pixels.
Pneumonia models that read the hospital (2018)|Networks trained to find pneumonia on chest X-rays partly learned which hospital (and which scanner) the image came from, and did worse at other hospitals.|Check what a model really uses: "shortcut learning" looks great on the test set and fails elsewhere.
Zillow Offers (2021)|Zillow bought houses at prices set by its model. When the market changed, it paid too much, lost hundreds of millions of dollars, closed the business and cut about a quarter of its staff.|Models drift when the world changes, and that is exactly when their errors cost most.
The Dutch childcare benefits scandal|A tax-office risk system, with nationality among its factors, wrongly flagged thousands of families as fraudsters and made them repay benefits. The government resigned in 2021.|Automated decisions about people need human review, explanations and a way to appeal.
Lawyers cite invented cases (2023)|Lawyers in a New York case (Mata v. Avianca) filed a brief with court decisions that ChatGPT had made up. The judge fined them $5,000.|Language models invent sources confidently; check every citation.
Samsung's code leak (2023)|Engineers pasted confidential source code and meeting notes into ChatGPT to get help. Samsung then banned generative AI tools at work.|Know where your prompts go and who can keep them.
Air Canada's chatbot (2024)|The airline's chatbot invented a refund rule for bereavement fares. A tribunal ruled that Air Canada had to honour it.|A company is responsible for what its chatbot tells customers.
The deepfake video call (2024)|An employee of the firm Arup in Hong Kong paid out about 25 million US dollars after a video call in which the "colleagues", including the finance chief, were deepfakes.|Confirm money requests through a second, known channel.
"""

    /** the pair | the first | the second | when it matters | #topic */
    const val PAIRS = """
Precision vs recall|**Precision**: of everything you flagged, how much was right? TP / (TP + FP).|**Recall**: of everything that was really there, how much did you find? TP / (TP + FN).|A spam filter needs precision (don't bin real mail); cancer screening needs recall (don't miss a case).|#eval
Overfitting vs underfitting|**Overfitting**: learns the training data by heart, noise included. Training score high, validation score much lower.|**Underfitting**: too simple to capture the pattern. Both scores are low.|Overfitting: more data, regularisation, a smaller model. Underfitting: a bigger model, better features, train longer.|#regular
Bias vs variance|**Bias**: error from wrong assumptions; the model is too simple and misses the pattern.|**Variance**: error from being too sensitive to the training sample; another sample gives a very different model.|More complexity lowers bias and raises variance; you look for the balance.|#regular
Parameters vs hyperparameters|**Parameters**: learned from the data during training (weights, biases).|**Hyperparameters**: chosen by you before training (learning rate, batch size, number of layers).|Tune hyperparameters on the validation set, never on the test set.|#optim
Training vs validation vs test set|**Training set**: fit the parameters. **Validation set**: compare models and tune hyperparameters.|**Test set**: one final, honest score at the end.|If you tune on the test set, its score is no longer honest.|#data
Epoch vs batch vs iteration|**Epoch**: one pass over the whole training set. **Batch**: the examples used for one update.|**Iteration (step)**: one update of the weights, i.e. one batch.|1,000 examples with batch size 100 → 10 iterations per epoch.|#optim
Classification vs regression|**Classification**: predict a category (spam / not spam, which digit).|**Regression**: predict a number (a price, a temperature).|The loss differs: cross-entropy for classification, mean squared error (or similar) for regression.|#classic
Supervised vs unsupervised vs self-supervised|**Supervised**: learn from labelled examples. **Unsupervised**: find structure without labels (clustering).|**Self-supervised**: make labels from the data itself, e.g. predict the next word or a masked word.|Language models are pre-trained self-supervised, so they can use the whole web without labels.|#classic
L1 vs L2 regularisation|**L1 (lasso)**: adds the sum of absolute weights; pushes many weights to exactly zero, a kind of feature selection.|**L2 (ridge, weight decay)**: adds the sum of squared weights; makes all weights small but rarely zero.|Use L1 when you expect few useful features; L2 is the everyday default.|#regular
Bagging vs boosting|**Bagging**: train many models independently on random samples and average them (random forest). Mainly lowers variance.|**Boosting**: train models one after another, each fixing the previous ones' errors (XGBoost). Mainly lowers bias.|Boosted trees are often the best choice for tables of data.|#classic
Batch norm vs layer norm|**Batch norm**: normalises each feature across the examples in a batch; behaves differently in training and inference.|**Layer norm**: normalises across the features of each single example; works the same for any batch size.|CNNs often use batch norm; Transformers use layer norm.|#nn
Generative vs discriminative models|**Generative**: models how the data itself is made, p(x) or p(x, y), so it can create new examples.|**Discriminative**: models only p(y given x), the boundary between classes.|Logistic regression is discriminative; GPT, diffusion models and naive Bayes are generative.|#gen
Encoder vs decoder (Transformers)|**Encoder** (BERT): sees the whole text in both directions; good for understanding, classification and embeddings.|**Decoder** (GPT): sees only earlier tokens and predicts the next; good for generating text.|Translation models such as T5 use both: an encoder reads, a decoder writes.|#transformer
Prompting vs RAG vs fine-tuning|**Prompting**: change only the instructions and examples. **RAG**: fetch relevant documents into the prompt at question time.|**Fine-tuning**: change the model's weights with more training.|Try prompting first; RAG for knowledge that changes or must be cited; fine-tuning for style, format or a narrow skill.|#llmuse
Token vs embedding|**Token**: a piece of text the model reads (a word, part of a word or a symbol), stored as an ID number.|**Embedding**: the vector of numbers the model uses for that token (or for a whole text).|Tokens are what you count for price and context length; embeddings are what you compare for similarity.|#embed
Logits vs probabilities|**Logits**: the raw scores a model outputs; any real numbers.|**Probabilities**: logits after softmax (or sigmoid); between 0 and 1 and summing to 1.|PyTorch's CrossEntropyLoss expects logits: don't apply softmax yourself first.|#nn
Sigmoid vs softmax|**Sigmoid**: one score → one probability; for yes/no, or several independent labels.|**Softmax**: several scores → probabilities that sum to 1; for exactly one class out of many.|A photo can be both "beach" and "sunset": use sigmoids, not softmax.|#nn
Cross-entropy vs mean squared error|**Cross-entropy**: compares predicted probabilities with the true class; the loss for classification.|**Mean squared error**: the average squared difference between numbers; the loss for regression.|MSE on probabilities learns slowly when the model is confidently wrong; cross-entropy punishes that hard.|#optim
False positive vs false negative|**False positive (type I error)**: a false alarm; you said yes, the truth was no.|**False negative (type II error)**: a miss; you said no, the truth was yes.|Which one costs more decides where you set the threshold.|#eval
ROC-AUC vs PR-AUC|**ROC-AUC**: recall against false-positive rate; 0.5 is guessing.|**PR-AUC**: precision against recall; its baseline is the share of positives.|With rare positives (fraud, disease) ROC-AUC looks flattering; PR-AUC shows the real difficulty.|#eval
Correlation vs causation|**Correlation**: two things move together.|**Causation**: changing one changes the other.|Models learn correlations; a hidden third factor or a shortcut can make them fail when the world changes.|#prob
Data drift vs concept drift|**Data drift**: the inputs change (new kinds of users, a new camera) while the rule stays the same.|**Concept drift**: the relationship itself changes (what counts as fraud, what people buy in a crisis).|Both lower accuracy over time; monitor inputs and results, and retrain.|#mlops
Data parallelism vs model parallelism|**Data parallelism**: every GPU holds the whole model and works on different examples; gradients are averaged.|**Model parallelism**: the model is split across GPUs (by layers, or tensors within layers).|Use model parallelism when the model doesn't fit on one GPU.|#mlops
Temperature vs top-p|**Temperature**: rescales the probabilities; low = safe and repetitive, high = varied and riskier.|**Top-p (nucleus sampling)**: samples only from the smallest set of tokens whose probabilities add up to p.|For facts and code use a low temperature; for brainstorming a higher one.|#llmuse
Keyword search vs semantic search|**Keyword search** (BM25): matches the words themselves; great for names, codes and exact terms.|**Semantic search**: compares embeddings; finds the same meaning in different words.|Good RAG systems often combine both (hybrid search).|#embed
Dropout vs weight decay|**Dropout**: randomly switches off units during training.|**Weight decay**: shrinks the weights a little at every step (L2).|Both fight overfitting; they're often used together.|#regular
Normalisation vs standardisation|**Normalisation (min–max)**: rescale a feature to 0–1.|**Standardisation (z-score)**: subtract the mean, divide by the standard deviation.|Distance-based models (k-NN, k-means, SVM) need scaled features; tree models don't.|#data
k-means vs k-nearest neighbours|**k-means**: unsupervised; splits the data into k clusters around centres.|**k-NN**: supervised; labels a point by a vote of its k closest labelled points.|Same letter k, different jobs: number of clusters vs number of neighbours.|#classic
Zero-shot vs few-shot|**Zero-shot**: only the instruction, no examples.|**Few-shot**: a handful of solved examples in the prompt.|Few-shot helps when the format or the judgement is hard to describe in words.|#llmuse
Pre-training vs fine-tuning|**Pre-training**: learn from huge amounts of general data; very expensive, done once.|**Fine-tuning**: adapt the pre-trained model with a small, task-specific dataset.|Almost nobody pre-trains; most teams fine-tune or just prompt existing models.|#llmtrain
Vanishing vs exploding gradients|**Vanishing**: gradients shrink layer after layer, so early layers stop learning.|**Exploding**: gradients grow layer after layer, so weights jump and the loss becomes NaN.|Vanishing: ReLU, residual connections, normalisation. Exploding: gradient clipping, a lower learning rate.|#optim
Accuracy vs balanced accuracy|**Accuracy**: share of all predictions that are right.|**Balanced accuracy**: the average of the recall of each class.|With 99% negatives, "always no" has 99% accuracy but only 50% balanced accuracy.|#eval
Object detection vs segmentation|**Object detection**: boxes around objects, with a label each.|**Segmentation**: a label for every pixel (semantic), or for every pixel of each separate object (instance).|Self-driving cars need both: boxes for cars, pixels for the road.|#cnn
fp16 vs bf16|**fp16**: 16-bit floats with more precision but a small range; can overflow and needs loss scaling.|**bf16**: 16-bit floats with the same range as fp32 but less precision.|bf16 is the usual choice for training on modern GPUs because it rarely overflows.|#mlops
Online vs batch inference|**Online (real-time)**: answer each request as it comes, in milliseconds.|**Batch**: score many examples at once on a schedule (e.g. every night).|Recommendations for tomorrow's email: batch. Fraud check at payment time: online.|#mlops
Agent vs workflow|**Workflow**: the steps are fixed in code; the LLM fills in each step.|**Agent**: the LLM decides the next step and which tool to use, in a loop.|Workflows are cheaper and more predictable; use an agent only when the steps can't be known in advance.|#llmuse
"""

    /** cloze text | explanation | #topic */
    const val FORMULAS = """
Softmax: softmax(z)ᵢ = {{c1::exp(zᵢ) / Σⱼ exp(zⱼ)}}|Turns scores (logits) into probabilities that sum to 1. Subtract the largest zᵢ first so exp doesn't overflow; the result is the same.|#nn
Sigmoid: σ(x) = {{c1::1 / (1 + e⁻ˣ)}}|Squashes any number into (0, 1). Its derivative is σ(x)·(1 − σ(x)), never more than 0.25: one reason deep sigmoid networks had vanishing gradients.|#nn
ReLU(x) = {{c1::max(0, x)}}|Cheap, and its gradient is 1 for positive inputs, so it doesn't vanish. Units that only ever get negative inputs "die".|#nn
A linear layer: y = {{c1::Wx + b}}|W is the weight matrix, b the bias. Without an activation between them, stacked linear layers collapse into one linear layer.|#linalg
Cross-entropy for one example: L = {{c1::− Σᵢ yᵢ log pᵢ}}; with a one-hot label it is just {{c2::− log p(true class)}}|Probability 0.9 on the right class costs 0.105; probability 0.01 costs 4.6. Confident mistakes are punished hard.|#optim
Binary cross-entropy: L = {{c1::− [y log p + (1 − y) log(1 − p)]}}|The loss for yes/no outputs from a sigmoid. Only one of the two terms is active for each example.|#optim
Mean squared error: MSE = {{c1::(1/n) Σ (yᵢ − ŷᵢ)²}}|The default loss for regression. Squaring makes big errors count much more, so outliers pull hard; MAE (absolute errors) is more robust.|#optim
Gradient descent: θ ← {{c1::θ − η ∇L(θ)}}|Take a small step (η, the learning rate) against the gradient, the direction in which the loss rises fastest.|#optim
Momentum: v ← {{c1::β v + ∇L(θ)}}, then θ ← θ − η v|Keeps a running direction like a ball rolling downhill: smooths noisy gradients and speeds up through flat valleys. β is often 0.9.|#optim
Adam keeps m ← β₁ m + (1 − β₁) g and {{c1::v ← β₂ v + (1 − β₂) g²}}, then θ ← θ − η m̂ / {{c2::(√v̂ + ε)}}|m is the average gradient, v the average squared gradient; m̂ and v̂ correct their start-up bias. Parameters with large gradients get smaller steps.|#optim
Chain rule: dL/dx = {{c1::dL/dy · dy/dx}}|Backpropagation is the chain rule applied layer by layer, from the loss back to the first weights.|#calc
Bayes' rule: P(A∣B) = {{c1::P(B∣A) · P(A) / P(B)}}|Posterior = likelihood × prior / evidence. A test that's 99% accurate for a 1-in-1,000 disease still gives mostly false alarms.|#prob
Precision = {{c1::TP / (TP + FP)}} and recall = {{c2::TP / (TP + FN)}}|Precision asks "how many of my yeses were right?"; recall asks "how many of the real yeses did I find?".|#eval
F1 = {{c1::2 · precision · recall / (precision + recall)}}|The harmonic mean: it stays low if either precision or recall is low.|#eval
Attention(Q, K, V) = {{c1::softmax(QKᵀ / √dₖ)}} · V|Each query is compared with every key; the weights mix the values. Dividing by √dₖ keeps the dot products from growing with the dimension and saturating the softmax.|#transformer
Cosine similarity: cos(a, b) = {{c1::(a · b) / (‖a‖ · ‖b‖)}}|1 = same direction, 0 = unrelated, −1 = opposite. The usual way to compare embeddings.|#embed
Entropy: H(p) = {{c1::− Σ p(x) log p(x)}}|Average surprise, in bits with log₂. A fair coin has 1 bit; a coin that always lands heads has 0.|#prob
KL divergence: KL(p ‖ q) = {{c1::Σ p(x) log (p(x) / q(x))}}|How much information is lost using q instead of p. Never negative, and not symmetric: KL(p ‖ q) ≠ KL(q ‖ p).|#prob
Perplexity = {{c1::exp(average cross-entropy per token)}}|"How many choices is the model torn between, on average?" Lower is better; 1 would be perfect.|#llmtrain
Standardisation: z = {{c1::(x − μ) / σ}}|Mean 0, standard deviation 1. Compute μ and σ on the training set only.|#data
Layer norm: y = {{c1::γ · (x − μ) / √(σ² + ε) + β}}|μ and σ² over the features of one example; γ and β are learned. ε avoids dividing by zero.|#nn
Logistic regression: p(y = 1 given x) = {{c1::σ(wᵀx + b)}}|A linear score turned into a probability. Despite the name, it's for classification.|#classic
The normal equation for linear regression: w = {{c1::(XᵀX)⁻¹ Xᵀ y}}|The exact least-squares solution. For many features gradient descent is cheaper than inverting XᵀX.|#linalg
Training compute of a Transformer ≈ {{c1::6 · N · D}} FLOPs (N parameters, D training tokens)|About 2 for the forward pass and 4 for the backward pass, per parameter per token. Generating one token costs about 2 · N.|#llmtrain
Q-learning: Q(s, a) ← Q(s, a) + α · [{{c1::r + γ · maxₐ′ Q(s′, a′)}} − Q(s, a)]|Move the estimate towards the reward plus the discounted value of the best next action. γ (gamma) sets how much the future counts.|#rl
Convolution output size = {{c1::(W − K + 2P) / S + 1}} (input W, kernel K, padding P, stride S)|32×32 input, 3×3 kernel, padding 1, stride 1 → 32×32. Stride 2 would halve it.|#cnn
Inverted dropout keeps each unit with probability p during training and scales it by {{c1::1 / p}}|So the expected activation is the same in training and at inference, where nothing is dropped.|#regular
"""

    /** symptom | likely causes | what to do | #topic */
    const val WRONG = """
The training loss suddenly becomes NaN after a few hundred steps.|Learning rate too high (gradients explode), log(0) or division by zero, or fp16 overflow.|Lower the learning rate or add warm-up, clip gradients, add a small ε, use bf16 or loss scaling.|#optim
Training accuracy is 99%, validation accuracy 70%.|Overfitting: the model memorised the training set.|More data or augmentation, dropout or weight decay, a smaller model, early stopping.|#regular
Training and validation loss are both high and barely move.|Underfitting or a bug: model too small, learning rate too low, uninformative features, labels misaligned with inputs.|First make the model overfit one small batch: if it can't, there's a bug in the pipeline.|#optim
Validation accuracy is suspiciously high (99%) on a hard problem.|Data leakage: duplicates across the splits, a feature that gives away the label, or preprocessing fitted on all the data.|Split first, fit scalers on the training set only, remove duplicates, look at the most important features.|#data
A fraud model is 99% accurate but catches almost no fraud.|Class imbalance: it predicts "not fraud" every time.|Look at precision, recall and PR-AUC; use class weights or resampling; tune the decision threshold.|#eval
The loss doesn't move at all from the first step.|Forgot optimizer.step() or zero_grad(), learning rate 0, frozen parameters, or the graph was detached.|Print the gradient norms: if they are zero or None, follow the graph back to find where it breaks.|#optim
The loss falls, then keeps spiking upwards.|Learning rate too high, no warm-up, or a few bad batches.|A learning-rate schedule with warm-up, gradient clipping, check the data in the spiking batches.|#optim
Excellent offline metrics, poor results in production.|Training–serving skew (features computed differently) or the live data differs from the training data.|Share the feature code between training and serving; log and compare live inputs with training inputs.|#mlops
Accuracy slowly falls over months in production.|Data drift or concept drift: the world changed.|Monitor inputs and results, label fresh data, retrain on a schedule or when drift is detected.|#mlops
CUDA out of memory.|Batch or sequence too long, a model too big, or loss tensors kept for logging (keeping the whole graph).|Smaller batches with gradient accumulation, mixed precision, gradient checkpointing, log loss.item() not loss.|#mlops
The GPU sits at 20% utilisation during training.|The data loading can't keep up.|More DataLoader workers, pin_memory, preprocess and cache the data once, use faster storage.|#mlops
Every run gives a different result.|Random seeds, data order and non-deterministic GPU operations.|Set seeds, use deterministic settings when debugging, and report the mean and spread over several seeds.|#eval
Predictions at inference are oddly different from what you saw in training.|The model is still in training mode: dropout is on and batch norm uses the current batch.|Call model.eval() (and use torch.no_grad()) before predicting.|#nn
Validation loss is lower than training loss.|Dropout and augmentation only apply in training, or the validation set is easier.|Usually fine; check that the splits come from the same distribution.|#regular
Gradients in the first layers of a deep network are almost zero.|Vanishing gradients: sigmoid or tanh activations, poor initialisation, no skip connections.|ReLU-family activations, He or Xavier initialisation, residual connections, normalisation layers.|#nn
k-means gives clusters that make no sense.|Features on very different scales, or the wrong number of clusters.|Standardise the features; try several k and compare (elbow method, silhouette score).|#classic
"70% confident" predictions are right 95% of the time.|The model is badly calibrated.|Calibrate on validation data: temperature scaling, Platt scaling or isotonic regression.|#eval
An X-ray model is great on its test set but fails at a new hospital.|Shortcut learning or domain shift: it learned hospital-specific details, not the disease.|Test on external data, inspect what the model looks at, train on data from many sources.|#data
The chatbot confidently makes up facts and sources.|Hallucination: the model generates plausible text, not checked facts.|Ground it with RAG and quote the sources, let it say "I don't know", lower the temperature, verify citations.|#llmuse
A RAG system retrieves chunks that don't answer the question.|Poor chunking, a weak embedding model, no reranking, or questions phrased very differently from the documents.|Tune chunk size and overlap, add keyword (hybrid) search and a reranker, rewrite the query.|#embed
The model ignores an instruction in the middle of a very long prompt.|"Lost in the middle": instructions buried in a long context get less attention.|Put key instructions at the start or end, cut unneeded context, give the prompt clear sections.|#llmuse
After fine-tuning, the model is worse at everything else.|Catastrophic forgetting.|A lower learning rate, fewer epochs, LoRA instead of full fine-tuning, mix in some general data.|#llmtrain
The model's text keeps repeating the same phrase.|Greedy decoding or a very low temperature.|Sample with a moderate temperature and top-p, add a repetition penalty.|#llmuse
A web page the assistant summarised made it leak private data.|Prompt injection: text from an untrusted source was treated as instructions.|Keep untrusted content separate from instructions, give tools the least access needed, ask a person before risky actions.|#llmuse
The model is great at the benchmark but users don't like it.|The benchmark doesn't measure what users need, or it leaked into the training data.|Build your own evaluation set from real tasks, and check for contamination.|#eval
"""

    /** question | answer | why | #topic */
    const val NUMBERS = """
Bytes per parameter in fp32, fp16/bf16, int8 and int4?|4 · 2 · 1 · ½|Quantising from fp16 to int4 makes a model about four times smaller.|#mlops
Memory for the weights of a 7-billion-parameter model in fp16?|About 14 GB|7 billion × 2 bytes. In 4-bit it fits in about 4 GB.|#mlops
Memory per parameter to fully fine-tune with Adam in mixed precision?|About 16 bytes|Weights (2) + gradients (2) + fp32 master weights (4) + Adam's two averages (4 + 4). A 7B model needs over 100 GB before activations.|#mlops
Compute to train a Transformer with N parameters on D tokens?|About 6 · N · D FLOPs|2 for the forward pass, 4 for the backward pass, per parameter per token.|#llmtrain
Compute to generate one token with an N-parameter model?|About 2 · N FLOPs|One multiply and one add per parameter.|#llmuse
Chinchilla's compute-optimal tokens per parameter?|About 20|So a 70B model wants about 1.4 trillion tokens. Many models now train far beyond this to be cheaper to run.|#llmtrain
How much text is one token, in English?|About 4 characters, or ¾ of a word|So 1,000 tokens ≈ 750 words. Other languages often need more tokens per word.|#llmuse
How many parameters does GPT-3 have?|175 billion|Trained on about 300 billion tokens (2020).|#llmtrain
The original Transformer (base model)?|6 encoder and 6 decoder layers, width 512, 8 heads, about 65 million parameters|"Attention Is All You Need", 2017.|#transformer
BERT-base and BERT-large?|110 million and 340 million parameters|12 layers, width 768 (base); 24 layers, width 1024 (large).|#transformer
How does the cost of self-attention grow with the sequence length n?|With n² (time and memory)|Every token is compared with every other one; doubling the context quadruples the attention cost.|#transformer
Size of the KV cache per token?|2 × layers × width × bytes per number|Keys and values for every layer. It's why long contexts need so much memory; grouped-query attention shrinks it.|#llmuse
ImageNet (the competition version)?|About 1.28 million training images in 1,000 classes|Plus 50,000 validation images.|#data
MNIST?|60,000 training and 10,000 test images of handwritten digits, 28 × 28 pixels, greyscale|The "hello world" of deep learning.|#data
AlexNet's top-5 error in 2012, against the runner-up?|15.3% against 26.2%|A gap of almost 11 points: the moment deep learning won computer vision.|#cnn
Estimated human top-5 error on ImageNet?|About 5%|Andrej Karpathy's own test (5.1%); ResNet got below it in 2015.|#cnn
Adam's default settings?|Learning rate 0.001, β₁ = 0.9, β₂ = 0.999, ε = 10⁻⁸|AdamW adds decoupled weight decay, often 0.01–0.1.|#optim
Typical learning rate to fine-tune a pre-trained Transformer?|About 1e-5 to 5e-5 (full fine-tuning); about 1e-4 to 2e-4 with LoRA|Much lower than from scratch, so you don't destroy what it already knows.|#llmtrain
Common sizes of text embeddings?|384 (small sentence models), 768 (BERT-base), 1,536 or more (large API models)|Bigger isn't always better: cost and storage grow with the size.|#embed
GPU memory on an NVIDIA H100?|80 GB|One H100 can't hold a 70B model in fp16 (≈ 140 GB); you need several, or quantisation.|#mlops
Accuracy of a random guess with 10 balanced classes?|10%|Always compare with a baseline: chance, or always predicting the most common class.|#eval
AUC of a classifier that guesses at random?|0.5|Below 0.5 means its scores are upside down.|#eval
95% margin of error for 90% accuracy measured on 1,000 test examples?|About ±1.9 points|1.96 × √(0.9 × 0.1 / 1000). A 1-point gain on 1,000 examples may be noise.|#prob
A usual train / validation / test split?|About 70/15/15 or 80/10/10|With millions of examples, 1% for validation and test can be plenty.|#data
What does temperature 0 mean when sampling?|Always take the most likely token (greedy)|Same prompt, (almost) the same answer. Temperature 1 samples from the model's own probabilities.|#llmuse
"""

    /** question | code with ____ | the full code | explanation | #topic */
    const val CODE = """
The three lines of a PyTorch training step, in order?|for x, y in loader:\n    ____\n    loss = loss_fn(model(x), y)\n    ____\n    ____|for x, y in loader:\n    optimizer.zero_grad()\n    loss = loss_fn(model(x), y)\n    loss.backward()\n    optimizer.step()|Clear the old gradients, compute new ones, then update. Forget zero_grad() and gradients add up across steps.|#optim
What must you call before predicting with a PyTorch model?|____\nwith ____:\n    preds = model(x)|model.eval()\nwith torch.no_grad():\n    preds = model(x)|eval() turns off dropout and makes batch norm use its running statistics; no_grad() saves memory by not building the graph.|#nn
What does nn.CrossEntropyLoss expect as input?|loss_fn = nn.CrossEntropyLoss()\nloss = loss_fn(____, targets)  # targets: class indices, shape (N,)|loss_fn = nn.CrossEntropyLoss()\nloss = loss_fn(logits, targets)  # logits: shape (N, C), no softmax|It applies log-softmax itself. Passing probabilities applies softmax twice and trains badly.|#nn
What is the output shape?|layer = nn.Linear(784, 128)\nx = torch.randn(32, 784)\nlayer(x).shape  # ____|layer = nn.Linear(784, 128)\nx = torch.randn(32, 784)\nlayer(x).shape  # torch.Size([32, 128])|A linear layer works on the last dimension: (batch, in) → (batch, out).|#linalg
What is the shape of a + b?|a = np.ones((3, 1))\nb = np.ones((1, 4))\n(a + b).shape  # ____|a = np.ones((3, 1))\nb = np.ones((1, 4))\n(a + b).shape  # (3, 4)|Broadcasting: dimensions of size 1 are stretched to match.|#linalg
Fill in a numerically stable softmax.|def softmax(x):\n    e = np.exp(x - ____)\n    return e / e.sum(axis=-1, keepdims=True)|def softmax(x):\n    e = np.exp(x - x.max(axis=-1, keepdims=True))\n    return e / e.sum(axis=-1, keepdims=True)|Subtracting the maximum keeps exp from overflowing and doesn't change the result.|#nn
How do you keep class proportions in a train/test split?|X_train, X_test, y_train, y_test = train_test_split(\n    X, y, test_size=0.2, ____, random_state=42)|X_train, X_test, y_train, y_test = train_test_split(\n    X, y, test_size=0.2, stratify=y, random_state=42)|stratify=y keeps the same share of each class in both parts; important with rare classes.|#data
Scale features without leaking the test set.|scaler = StandardScaler()\nX_train_s = scaler.____(X_train)\nX_test_s = scaler.____(X_test)|scaler = StandardScaler()\nX_train_s = scaler.fit_transform(X_train)\nX_test_s = scaler.transform(X_test)|Fit only on training data; the test set is transformed with the training mean and standard deviation.|#data
Chain preprocessing and a model in scikit-learn.|model = ____(StandardScaler(), LogisticRegression())\nmodel.fit(X_train, y_train)|model = make_pipeline(StandardScaler(), LogisticRegression())\nmodel.fit(X_train, y_train)|A pipeline refits the scaler inside each cross-validation fold, so nothing leaks.|#classic
Five-fold cross-validation in one line?|scores = ____(model, X, y, cv=5)\nprint(scores.mean(), scores.std())|scores = cross_val_score(model, X, y, cv=5)\nprint(scores.mean(), scores.std())|Five train/validation splits; report the mean and the spread.|#eval
Clip gradients in PyTorch: where does the line go?|loss.backward()\n____\noptimizer.step()|loss.backward()\ntorch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)\noptimizer.step()|After the gradients exist and before the update: caps their total size to stop explosions.|#optim
Shuffle the training data each epoch.|train_loader = DataLoader(train_ds, batch_size=32, ____)|train_loader = DataLoader(train_ds, batch_size=32, shuffle=True)|Shuffle for training, not for validation or test (order doesn't matter there, and fixed order is easier to debug).|#data
Run on the GPU when there is one.|device = "cuda" if ____ else "cpu"\nmodel = model.to(device)\nx = x.to(device)|device = "cuda" if torch.cuda.is_available() else "cpu"\nmodel = model.to(device)\nx = x.to(device)|The model and its inputs must be on the same device.|#mlops
Log the loss without keeping the graph in memory.|running_loss += ____|running_loss += loss.item()|loss.item() is a plain Python number; adding the tensor keeps every step's graph alive and leaks memory.|#mlops
Save and load a PyTorch model's weights.|torch.save(model.____(), "model.pt")\nmodel.____(torch.load("model.pt"))|torch.save(model.state_dict(), "model.pt")\nmodel.load_state_dict(torch.load("model.pt"))|Save the state dict (the weights), not the whole object: it survives code changes.|#mlops
Flatten feature maps before a linear layer.|x = x.view(x.size(0), ____)|x = x.view(x.size(0), -1)|Keep the batch dimension; -1 lets PyTorch work out the rest. torch.flatten(x, 1) does the same.|#cnn
Load a Hugging Face model and generate text.|tok = AutoTokenizer.from_pretrained(name)\nmodel = ____.from_pretrained(name)\ninputs = tok("Once upon a time", return_tensors="pt")\nout = model.____(**inputs, max_new_tokens=40)\nprint(tok.decode(out[0], skip_special_tokens=True))|tok = AutoTokenizer.from_pretrained(name)\nmodel = AutoModelForCausalLM.from_pretrained(name)\ninputs = tok("Once upon a time", return_tensors="pt")\nout = model.generate(**inputs, max_new_tokens=40)\nprint(tok.decode(out[0], skip_special_tokens=True))|"CausalLM" = a decoder that predicts the next token. generate() runs the sampling loop for you.|#llmuse
The quickest Hugging Face classifier?|clf = ____("sentiment-analysis")\nclf("I love this phone!")|clf = pipeline("sentiment-analysis")\nclf("I love this phone!")  # [{'label': 'POSITIVE', 'score': 0.99…}]|pipeline() picks a default model, tokenizer and post-processing for the task.|#llmuse
Add LoRA adapters with the PEFT library.|config = LoraConfig(r=8, lora_alpha=16, target_modules=["q_proj", "v_proj"])\nmodel = ____(model, config)\nmodel.print_trainable_parameters()|config = LoraConfig(r=8, lora_alpha=16, target_modules=["q_proj", "v_proj"])\nmodel = get_peft_model(model, config)\nmodel.print_trainable_parameters()|Only the small adapter matrices train, often well under 1% of the parameters.|#llmtrain
Cosine similarity of two embeddings in NumPy.|sim = a @ b / (____ * ____)|sim = a @ b / (np.linalg.norm(a) * np.linalg.norm(b))|If the embeddings are already normalised to length 1, the dot product alone is the cosine similarity.|#embed
Count the missing values in each column with pandas.|df.____().sum()|df.isna().sum()|Look before you train: then drop, fill or flag the gaps.|#data
Average price per city with pandas.|df.____("city")["price"].mean()|df.groupby("city")["price"].mean()|Split by a column, then apply a function to each group.|#data
Make a PyTorch experiment repeatable.|random.seed(0)\nnp.random.seed(0)\n____(0)|random.seed(0)\nnp.random.seed(0)\ntorch.manual_seed(0)|Seed every library that draws random numbers. Some GPU operations stay non-deterministic unless you ask otherwise.|#eval
Stop training when validation loss stops improving.|if val_loss < best:\n    best, bad = val_loss, 0\n    torch.save(model.state_dict(), "best.pt")\nelse:\n    bad += 1\n    if bad >= ____:\n        break|if val_loss < best:\n    best, bad = val_loss, 0\n    torch.save(model.state_dict(), "best.pt")\nelse:\n    bad += 1\n    if bad >= patience:\n        break|Early stopping with "patience": keep the best checkpoint, stop after a few epochs without improvement.|#regular
"""

    /** tool | what it is for | #topic */
    const val TOOLS = """
PyTorch|The most used deep-learning framework: tensors on the GPU, automatic gradients, and code that runs like normal Python.|#nn
TensorFlow and Keras|Google's deep-learning framework; Keras is its simple, high-level API for building and training models.|#nn
JAX|NumPy with automatic gradients, compiled to fast code (XLA), with vmap and pmap for batching and many devices. Popular at Google DeepMind.|#nn
NumPy|Fast n-dimensional arrays and maths in Python; the base of almost every other data library.|#linalg
pandas|Tables (data frames) in Python: load, clean, join, group and summarise data.|#data
scikit-learn|Classic machine learning: regression, trees, SVMs, clustering, pipelines, cross-validation and metrics.|#classic
XGBoost and LightGBM|Gradient-boosted decision trees: often the best models for tabular data, and fast.|#classic
Hugging Face Transformers|Thousands of pre-trained models with their tokenizers, downloaded from the Hugging Face Hub in a couple of lines.|#llmuse
PEFT|Hugging Face's library for parameter-efficient fine-tuning, such as LoRA adapters.|#llmtrain
vLLM|A fast server for language models: PagedAttention manages the KV cache in pages and continuous batching keeps the GPU busy.|#mlops
llama.cpp and Ollama|Run quantised language models (GGUF files) on a laptop or a CPU; Ollama makes it a one-line install.|#llmuse
LangChain and LlamaIndex|Frameworks for LLM apps: chain calls, connect tools and build retrieval (RAG) over your documents.|#llmuse
FAISS|Meta's library for fast nearest-neighbour search over millions of vectors.|#embed
Vector databases (Qdrant, Weaviate, Pinecone, Chroma, pgvector)|Store embeddings with their metadata and find the most similar ones quickly; pgvector adds this to PostgreSQL.|#embed
MLflow|Track experiments (parameters, metrics, models) and keep a registry of model versions.|#mlops
Weights & Biases|Dashboards for training runs, hyperparameter sweeps and comparing experiments.|#mlops
DVC|Version large data files and models next to your Git code.|#mlops
Docker|Packs an app with everything it needs into a container that runs the same everywhere.|#mlops
Kubernetes|Runs and scales containers across many machines; restarts them when they fail.|#mlops
Airflow|Schedules and monitors data pipelines written as graphs of tasks (DAGs).|#mlops
Ray|Distributed Python: spread training, tuning and serving over a cluster.|#mlops
DeepSpeed and PyTorch FSDP|Train models too big for one GPU by sharding weights, gradients and optimiser state across GPUs (ZeRO).|#mlops
CUDA|NVIDIA's platform for programming GPUs; the deep-learning frameworks are built on it.|#mlops
ONNX|An open file format for models, to move them between frameworks and fast inference runtimes.|#mlops
TensorRT|NVIDIA's tool that optimises trained models for fast inference on its GPUs.|#mlops
Jupyter|Notebooks that mix code, results, charts and notes: the usual place to explore data.|#data
OpenCV|Classic computer vision: read and transform images and video, detect edges, track objects.|#cnn
spaCy|Fast natural-language processing pipelines: tokens, entities, parts of speech.|#seq
Gradio and Streamlit|Turn a model into a small web demo with a few lines of Python.|#mlops
Model Context Protocol (MCP)|An open standard (Anthropic, 2024) for connecting AI apps to tools and data sources through one interface.|#llmuse
"""

    /** technique | what it is and why it works | how "The Long Road to Thinking Machines" prompt does it | #topic */
    const val PROMPTS = """
Give the piece a title|A working title tells the model what the whole thing is about and sets its tone before any detail.|"Turn the sources into a narrated story called 'The Long Road to Thinking Machines'": the title alone promises a journey.|#llmuse
State the task in the first sentence|Lead with the one thing you want done; everything after it is detail. The model reads the rest in the light of that sentence.|It opens with "Turn the sources into a narrated story…", not with background.|#llmuse
Name the format you want, and the one you don't|Saying what to avoid is as useful as saying what to do when the model has a default it falls back to.|"Structure it as a story in chapters, not a list of facts": it blocks the default bullet-point summary.|#llmuse
Give the structure as a numbered outline|A numbered list of parts fixes the order, the coverage and the boundaries, so nothing is skipped or merged.|Six chapters, each with a name and a date range: "1. The Dream (1943–1959)…".|#llmuse
Name each part evocatively|Short memorable names give each section a mood and a role in the story, not just a topic.|"Hype and the First Winter", "Quiet Builders", "The Deep Learning Explosion".|#llmuse
List the key items per part|Naming what must appear in each section stops the model from guessing what matters, and keeps it on your sources.|Chapter 4 lists LeNet, TD-Gammon, SVMs, LSTM, Deep Blue, deep belief networks, ImageNet.|#llmuse
Pin down the voice with adjectives|Two or three precise adjectives describe a whole style more compactly than a paragraph of rules.|"A dramatic, cinematic narrator."|#llmuse
Give the story an emotional shape|Tell the model where the highs and lows are; a narrative needs tension and release, not equal weight on everything.|"Treat the winters as low points and the comebacks as turning points."|#llmuse
Set an accuracy guardrail|When style is dramatic, say explicitly that facts must not bend to it.|"Keep every date and name accurate to the sources."|#llmuse
Set a length budget per part|A concrete limit per section keeps the parts balanced and the whole a predictable length.|"Keep each chapter to about 2 minutes."|#llmuse
Say how to handle jargon|Name the audience's level by saying what to do with hard words, not by describing the audience.|"Explain jargon in one plain sentence."|#llmuse
Ask for a specific ending|Endings drift into vague summaries unless you say what the last part should do.|"End with a reflection on what the pattern of hype, disappointment and breakthrough suggests about what comes next."|#llmuse
Separate content from visuals|Put look-and-feel in its own paragraph so it can be reused or dropped for outputs that have no visuals.|The last paragraph starts "Visual style (for slides, video or infographic output):".|#llmuse
Use exact values for colours|A hex code removes all doubt; "dark blue" can mean a hundred things.|"near-black navy or charcoal (#0B0F1A)".|#llmuse
Tie visuals to meaning|Map each visual choice to a part of the story so the design carries the narrative.|"Icy blue tones for AI winter chapters and warm amber for breakthroughs."|#llmuse
Add one recurring visual anchor|A single element that stays across sections makes many slides feel like one piece and shows progress.|"A thin horizontal timeline that stays visible across chapters."|#llmuse
End with a hard rule|A short, absolute rule at the end is easy to obey and easy to check.|"No white backgrounds."|#llmuse
Keep the source separate from the prompt|Facts go in a source file, instructions in the prompt: the facts can be checked and reused, and the prompt stays short.|ai_history_source.md holds "Event · Year · What happened" rows; the prompt only says what to do with them.|#llmuse
Give the source a clear format line|A header such as "Format: Event · Year · What happened" tells the model (and you) how to read every row.|The source file starts with its format, then one event per line.|#llmuse
Write each fact as what plus why|A fact with its consequence gives the narrator something to dramatise, not just a date to read out.|"Funding for neural networks dried up for more than a decade" after the "Perceptrons" book.|#llmuse
"""
}
