# Pravah — Theory Curriculum
Pravah — Theory Curriculum
Phase 1: Distributed Systems Fundamentals
1.1 CAP Theorem & How Pravah Makes Consistency Choices
## 🧩 What Problem Are We Solving?
Before CAP, ask yourself — why is building Pravah hard?

When Pravah runs on a single machine:

┌─────────────────────────────────┐
│         Single Machine          │
│                                 │
│  User ──► App ──► Database      │
│                                 │
│  Write "Pipeline X = RUNNING"   │
│  Read  "Pipeline X = RUNNING"   │
│                                 │
│  ✅ Always correct              │
│  ✅ Always available            │
│  ✅ Always fast                 │
└─────────────────────────────────┘
Life is simple. One machine. One truth.

But Pravah cannot run on a single machine in production because:

Single Machine Problems          Pravah's Reality
──────────────────────           ────────────────
If it crashes → everything       Needs to survive crashes
goes down

Can't handle 10,000 pipelines    Needs to scale horizontally
on one box

One datacenter = one point       Needs to run across regions
of failure                       for enterprise clients
So we split across multiple machines. And the moment we do that — CAP Theorem walks in the door.

🔺 The CAP Triangle
                    Consistency
                         △
                        /|\
                       / | \
                      /  |  \
                     /   |   \
                    /    |    \
                   /  CP | CA  \
                  /      |      \
                 /       |       \
                /        |        \
               /   AP    |         \
              /          |          \
             △───────────────────────△
        Availability           Partition
                               Tolerance
C — Consistency Every node in the system returns the same, most recent data at all times. If you write to Node A, reading from Node B immediately after gives you that same write.

A — Availability Every request always gets a response — never an error, never a timeout. The system is always up and serving.

P — Partition Tolerance The system keeps working even when network communication between nodes breaks down.

🌐 What Is a Network Partition?
This is critical to understand visually:

NORMAL STATE — everything connected
─────────────────────────────────────

  [Scheduler]──────[Runner Registry]
       │                  │
       └────[Job Store]───┘

  ✅ All nodes talk to each other
  ✅ Writes replicate instantly

PARTITION — network breaks
─────────────────────────────────────

  [Scheduler]  ╳╳╳╳╳╳  [Runner Registry]
       │          ↑            │
       │     Network cut       │
       └────[Job Store]        │
                    ╳╳╳╳╳╳╳╳╳╳┘

  ❌ Scheduler can't reach Runner Registry
  ❌ Writes on one side don't reach other side
  ❓ What does the system do now?
A partition can happen because:

A network cable fails
A Kubernetes pod restarts
A datacenter loses connectivity
A cloud provider has an outage
Even just high latency can look like a partition
Partitions are not rare. They happen in production regularly.

💡 Why P Is Not Optional
If you say "I don't want partition tolerance"
it means:
  → When network breaks, system shuts down completely
  → For Pravah: all pipelines stop, all runners disconnect
  → Enterprise clients lose their data workflows
  → This is unacceptable

Therefore P is MANDATORY.
The real question becomes:

When a partition happens —
what do you sacrifice: C or A?
⚔️ CP vs AP — The Real Choice
Let's make this concrete with a Pravah scenario:

SCENARIO
────────
Runner Registry has 2 nodes: Node A and Node B
Both track which runners are alive

Runner "pravah-runner-001" registers on Node A
Network partition happens
User queries Node B: "Is runner-001 alive?"
If Pravah chooses CP:

Node B:  "I can't reach Node A to verify"
         "I refuse to give potentially wrong answer"
         "Returning ERROR to user"

User sees: 503 Service Unavailable

✅ Data is never wrong
❌ System is unavailable during partition
If Pravah chooses AP:

Node B:  "I can't reach Node A"
         "But I'll serve what I last knew"
         "Runner-001 status: UNKNOWN (stale)"

User sees: Stale but honest response

✅ System stays available
❌ Data might be temporarily wrong
🏗️ Pravah's CAP Decisions — Component by Component
This is where good system design shines. Different components make different choices:

┌──────────────────────────────────────────────────────────────────┐
│                    PRAVAH CAP MAP                                │
├─────────────────────────┬───────┬──────────────────────────────┤
│ Component               │ CP/AP │ Reasoning                    │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Pipeline Definitions    │  CP   │ Running wrong version of a   │
│ (PostgreSQL primary)    │       │ pipeline = data corruption   │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Job Run Status          │  AP   │ Showing stale status on UI   │
│ (Redis cache)           │       │ is fine. Erroring is not.    │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Runner Heartbeats       │  AP   │ Slightly stale heartbeat     │
│ (Redis)                 │       │ is acceptable for ~30s       │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Secret Store            │  CP   │ Stale credentials = broken   │
│ (HashiCorp Vault)       │       │ or insecure pipelines        │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Kafka Event Log         │  CP   │ Event ordering & durability  │
│                         │       │ is non-negotiable            │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Data Catalog            │  AP   │ Stale metadata is annoying   │
│ (Elasticsearch)         │       │ but not catastrophic         │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Scheduler Leader        │  CP   │ Two schedulers thinking      │
│ Election (Redis lock)   │       │ they're leader = chaos       │
├─────────────────────────┼───────┼──────────────────────────────┤
│ Audit Logs              │  CP   │ Compliance requires          │
│ (PostgreSQL)            │       │ complete, accurate logs      │
└─────────────────────────┴───────┴──────────────────────────────┘
🧠 A Mental Model That Sticks
Think of it like a hospital vs a news website:

HOSPITAL (CP)
─────────────
Patient record must be accurate.
If two systems disagree on medication dosage
→ return ERROR, don't guess
→ doctor waits, gets correct record
Consistency > Availability

NEWS WEBSITE (AP)
─────────────────
Article view count might show 9,999 instead of 10,000
during a partition. That's fine.
→ show slightly stale count
→ never show error page
Availability > Consistency

PRAVAH
──────
Pipeline definitions = Hospital (CP)
Job status on dashboard = News website (AP)
⚖️ Trade-offs Summary
         CHOOSE CP WHEN                    CHOOSE AP WHEN
         ─────────────                     ──────────────
  Wrong data causes harm             Stale data is tolerable
  Financial / compliance data        User-facing displays
  Security credentials               Metrics & dashboards
  Pipeline version to execute        Search & catalog
  Leader election                    Heartbeat tracking
🎯 How Senior Interviewers Test This
They never ask "explain CAP theorem." They ask:

❓ "What happens to Pravah's scheduler if
    the database becomes unreachable?"

Good answer:
  "Scheduler service is CP — it will stop
   dispatching new jobs rather than risk
   dispatching based on stale pipeline config.
   In-flight jobs continue via runner-side
   checkpointing. We alert on-call immediately."

❓ "Two scheduler instances both think they're
    the leader — what happens?"

Good answer:
  "This is the split-brain problem during partition.
   We prevent it with leader election using Redis
   distributed lock with fencing tokens — only one
   instance holds the lock at a time. We cover this
   in detail in topic 1.4."

❓ "Your UI shows a pipeline as RUNNING but
    the runner crashed 2 minutes ago — why?"

Good answer:
  "Job status cache in Redis is AP — it serves
   last known state during partition. The reconciliation
   loop detects missed heartbeats within 30s and
   marks the job FAILED. This is an intentional
   trade-off: availability of the UI over
   perfect real-time accuracy."
## 📌 Key Takeaways
1. Network partitions WILL happen — P is mandatory

2. Real choice is always CP or AP per component

3. No system is purely CP or AP —
   Pravah uses both depending on the data

4. CP = correctness over availability
   AP = availability over correctness

5. CAP is the starting point —
   next we go deeper into what
   "consistency" actually means
## ⏭️ What's Next
CAP says "be consistent or be available." But what does consistent actually mean?

Turns out there are many levels — from ultra-strict to very loose — and Pravah uses different levels in different places.

Next: 1.2 — Consistency Models Strong, Sequential, Causal, Eventual — what each means and exactly where Pravah uses each one.
