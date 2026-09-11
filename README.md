# AI Interview Booth — Backend (Spring Boot)

This is the backend for your final-year project. It gives your existing HTML/JS
frontend a real server: user accounts, a database, and an AI service that
generates interview questions and scores answers using the Claude API.

## What's inside

```
interview-booth-backend/
  pom.xml
  src/main/java/com/interviewbooth/
    InterviewBoothApplication.java   <- main() entry point
    model/        User, InterviewSession, Answer  (database tables)
    repository/   Spring Data JPA repositories
    dto/          Request/response shapes for the API
    security/     JWT creation/validation, login filter
    config/       Spring Security + CORS setup
    service/      AuthService, InterviewService, AIService (the Claude calls)
    controller/   REST endpoints your frontend calls
  src/main/resources/application.properties
```

## 1. Prerequisites

- Java 17+ (`java -version` to check)
- Maven (or use the `mvnw` wrapper if you generate one via Spring Initializr —
  see note at the bottom)
- An Anthropic API key from https://console.anthropic.com (optional at first —
  the app runs without one using fallback logic, so you can get everything
  else working before you touch the AI part)

## 2. Run it

```bash
cd interview-booth-backend

# optional but recommended: enables real AI question generation & scoring
export ANTHROPIC_API_KEY=sk-ant-your-key-here

mvn spring-boot:run
```

The server starts on `http://localhost:8080`. It uses an in-memory H2 database
by default, so there's zero database setup needed to get going — data resets
each time you restart the app. When you're ready for something permanent,
install MySQL and follow the comment block in `application.properties` to
switch over (uncomment the MySQL lines, comment out the H2 lines, add the
`mysql-connector-j` dependency which is already in `pom.xml`).

## 3. API endpoints

| Method | Path                        | Auth?  | Purpose |
|--------|-----------------------------|--------|---------|
| POST   | `/api/auth/register`        | No     | Create account: `{fullName, email, password, education, preferredRole}` |
| POST   | `/api/auth/login`           | No     | Log in: `{email, password}` → returns `{token, fullName, email, role}` |
| POST   | `/api/interview/start`      | Yes    | `{role, difficulty, roundType}` → returns `{sessionId, questions:[{id,text}]}` |
| POST   | `/api/interview/answer`     | Yes    | `{sessionId, questionText, userAnswer}` → returns scored `Answer` |
| POST   | `/api/interview/finish/{id}`| Yes    | Finalizes a session, computes average score |
| GET    | `/api/history`              | Yes    | Returns all past sessions for the logged-in user |

"Yes" under Auth means you must send the JWT from login/register as a header:
`Authorization: Bearer <token>`

## 4. Wiring up your existing frontend

Your HTML file currently keeps everything in a JS object called `DB` and never
talks to a server. To connect it to this backend, replace the parts that read
from `DB` with `fetch()` calls. Rough sketch:

```js
const API_BASE = "http://localhost:8080/api";
let authToken = null; // store after login

async function login(email, password) {
    const res = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
    });
    const data = await res.json();
    if (res.ok) {
        authToken = data.token;
        localStorage.setItem("token", data.token); // simplest option for a college project
    }
    return data;
}

async function startInterview(role, difficulty, roundType) {
    const res = await fetch(`${API_BASE}/interview/start`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${authToken}`
        },
        body: JSON.stringify({ role, difficulty, roundType })
    });
    return res.json(); // { sessionId, questions }
}

async function submitAnswer(sessionId, questionText, userAnswer) {
    const res = await fetch(`${API_BASE}/interview/answer`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${authToken}`
        },
        body: JSON.stringify({ sessionId, questionText, userAnswer })
    });
    return res.json(); // { score, feedback, ... }
}
```

Concretely in your file:
- `app.initiateBooth()` currently reads `DB.questionPool[...]` synchronously —
  change it to `await startInterview(role, diff, type)` and use the
  `questions` array it returns to populate `session.questions`.
- `app.submitAnswer()` currently computes `score` locally with a word-count
  formula — replace that with `await submitAnswer(sessionId, currentQ.text, text)`
  and use the `score`/`feedback` fields the server sends back.
- Add a login/register screen (`app.navigate('login')`) before `dashboard`,
  since routes now require a token.

I can build out that login screen and rewire the specific functions in your
HTML file next if you'd like — just say the word.

## 5. A note on the AI model

`AIService.java` calls Anthropic's API directly with plain Java (no extra SDK
needed), using the `claude-sonnet-5` model. If your college specifically wants
you to show "prompt engineering," the two prompts worth polishing are in
`AIService.generateQuestions()` and `AIService.scoreAnswer()`.

## 6. If `mvn` isn't installed

Easiest fix: go to https://start.spring.io, generate a project with Java 17,
Spring Boot 3.2.x, and the same dependencies as this `pom.xml`, download it —
it comes with a `mvnw` wrapper script so you don't need Maven installed
globally — then copy this `src/` folder on top of the generated one.
