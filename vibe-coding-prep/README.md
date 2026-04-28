Campus Lost & Found Hub
A practice project for the CCE Week Vibe Coding Competition.

This is a full, working web system you can study, run, and adapt to whatever problem statement is revealed on competition day. It is intentionally simple so you can read every line.

Table of contents
What it does
Why this design fits the competition criteria
Project structure
Step-by-step setup (Windows / Mac / Linux)
How to run it
How to test the APIs (for QA)
Git & GitHub workflow (for DevOps)
How to adapt to a different problem on competition day
Troubleshooting
1. What it does
A campus Lost & Found system.

Students can sign up, post a LOST item, post a FOUND item, search items, and claim something they recognise.
Admins can see all items, see all users, and read the activity log.
Two external APIs are used:
OpenStreetMap Nominatim – converts a typed location like "University of Mindanao Library" into latitude/longitude.
UI Avatars – generates a profile picture from a name.
All actions are written to an activity_logs table in MySQL and to a text file backend/app.log.
2. How this maps to the judging criteria
Criterion	%	Where you can point to in this project
Logic & Functionality	25	AuthApi.java, ItemsApi.java, ClaimsApi.java
Database Design	15	database/schema.sql (4 tables, foreign keys, enums)
Documentation & Implementation	15	This README + comments in every Java file
AI Utilization	10	Use ChatGPT / Gemini during the round to extend handlers
System Architecture & Code Quality	10	Clear handlers/ and util/ folders, no copy-pasted code
Technical Explanation & Defense	10	You can walk a judge through one request end-to-end
Role & Team Collaboration	10	Frontend, backend, DB, docs all separated cleanly
User Interface & Experience	5	frontend/style.css – clean cards, responsive layout
Required technical features (from the mechanics):

Functional system modules ✔ (auth, items, claims, admin)
Database integration, no hard-coded data ✔ (everything goes through MySQL)
Basic security (authentication, roles) ✔ (SHA-256 passwords + STUDENT/ADMIN roles + session tokens)
API integration, at least 2 ✔ (Nominatim + UI Avatars)
Error handling and system logging ✔ (AppLogger writes to file + DB)
3. Project structure
vibe-coding-prep/
├── README.md                     ← you are here
├── database/
│   └── schema.sql                ← run this once in MySQL
├── backend/
│   ├── lib/                      ← put mysql-connector-j-x.x.x.jar here
│   ├── src/
│   │   ├── Main.java             ← starts the HTTP server, registers routes
│   │   ├── handlers/
│   │   │   ├── AuthApi.java      ← /api/auth/*       (signup, login, logout, me)
│   │   │   ├── ItemsApi.java     ← /api/items/*      (list, create, get, delete)
│   │   │   ├── ClaimsApi.java    ← /api/claims/*     (claim, approve, reject)
│   │   │   ├── AdminApi.java     ← /api/admin/*      (users, logs, stats)
│   │   │   ├── ExternalApi.java  ← /api/external/*   (geocode, avatar)
│   │   │   └── StaticFiles.java  ← serves files from frontend/
│   │   └── util/
│   │       ├── Database.java     ← opens the JDBC connection
│   │       ├── Http.java         ← request helpers (read body, send JSON, auth)
│   │       ├── Json.java         ← tiny JSON reader/writer (no library)
│   │       ├── Sessions.java     ← in-memory token store
│   │       ├── Passwords.java    ← SHA-256 password hashing
│   │       └── AppLogger.java    ← writes to app.log AND activity_logs table
│   └── build/                    ← compiled .class files (auto-created)
├── frontend/
│   ├── index.html                ← login + signup
│   ├── dashboard.html            ← main page after login
│   ├── admin.html                ← admin-only page
│   ├── style.css
│   └── app.js                    ← shared fetch helpers
└── .gitignore

4. Step-by-step setup
4.1 Install the things you need (one time)
Tool	Why	Where to get it
JDK 17 or newer	Compile and run the Java backend	https://adoptium.net
MySQL 8	Database	https://dev.mysql.com/downloads/mysql/
MySQL Workbench (optional)	Visual DB tool	comes with the MySQL installer
VS Code	Editor	https://code.visualstudio.com
Git	Version control	https://git-scm.com
mysql-connector-j-9.x.x.jar	Lets Java talk to MySQL	https://dev.mysql.com/downloads/connector/j/ → "Platform Independent" → ZIP
After downloading the connector zip, extract it and copy the file mysql-connector-j-9.x.x.jar into vibe-coding-prep/backend/lib/.

Verify Java is installed:

java -version
javac -version

You should see version 17 or higher.

4.2 Create the database
Open a terminal and start the MySQL command-line client (or use Workbench):

mysql -u root -p

Then run:

SOURCE /full/path/to/vibe-coding-prep/database/schema.sql;

This creates a database called lost_and_found with 4 tables and a default admin account.

Default admin login: admin / admin123

4.3 Tell the backend how to reach MySQL
Open backend/src/util/Database.java and change these three constants if your MySQL setup is different:

private static final String URL  = "jdbc:mysql://localhost:3306/lost_and_found";
private static final String USER = "root";
private static final String PASS = "your_mysql_password";

You can also set them as environment variables:

export DB_URL="jdbc:mysql://localhost:3306/lost_and_found"
export DB_USER="root"
export DB_PASS="your_mysql_password"

The code reads env vars first, then falls back to the constants.

5. How to run it
From the vibe-coding-prep/ folder:

Compile
javac -d backend/build -cp "backend/lib/*" backend/src/Main.java backend/src/handlers/*.java backend/src/util/*.java

Run
Linux / Mac:

java -cp "backend/build:backend/lib/*" Main

Windows (PowerShell):

java -cp "backend/build;backend/lib/*" Main

You should see:

[INFO] Database connected: lost_and_found
[INFO] Server started at http://localhost:8080

Open http://localhost:8080 in your browser. Log in with admin / admin123 (or sign up a new student).

To stop the server, press Ctrl+C in the terminal.

6. How to test the APIs (QA section)
The backend uses JSON over HTTP. You can test with the browser's network tab, with curl, or with Postman.

Sign up a new user
curl -X POST http://localhost:8080/api/auth/signup \
     -H "Content-Type: application/json" \
     -d '{"full_name":"Juan Dela Cruz","username":"juan","password":"pass123"}'

Log in (returns a token)
curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"juan","password":"pass123"}'

Copy the token value from the response.

Create a LOST item (use the token)
curl -X POST http://localhost:8080/api/items \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"type":"LOST","title":"Black Wallet","description":"Brown leather, has my ID","category":"Personal","location":"Cafeteria"}'

List all items
curl http://localhost:8080/api/items

Use the geocoding API (external API #1)
curl "http://localhost:8080/api/external/geocode?q=University+of+Mindanao"

Use the avatar API (external API #2)
curl "http://localhost:8080/api/external/avatar?name=Juan+Dela+Cruz"

View activity logs (admin only)
curl http://localhost:8080/api/admin/logs \
     -H "Authorization: Bearer ADMIN_TOKEN"

Suggested QA test cases to demo to judges
#	Test	Expected
1	Sign up with a username that already exists	409 Conflict
2	Log in with the wrong password	401 Unauthorized
3	Create an item without a token	401 Unauthorized
4	Student calls /api/admin/logs	403 Forbidden
5	Geocode an empty string	400 Bad Request
6	Stop MySQL and try to log in	500, with friendly error in app.log
7	Two users claim the same item	both rows created, status PENDING
8	Owner approves one claim	item status becomes CLAIMED, log entry added
7. Git & GitHub workflow (DevOps section)
The mechanics say:

GitHub repository must be created only after the timer starts. Teams must show clear commit history.

So practice this flow now:

# 1. Initialise inside vibe-coding-prep/
cd vibe-coding-prep
git init
git branch -M main
# 2. First commit – just the empty skeleton
git add .gitignore README.md database/schema.sql
git commit -m "chore: initial project skeleton and schema"
# 3. Backend foundation
git add backend/src/Main.java backend/src/Database.java backend/src/util/
git commit -m "feat(backend): http server, DB connection, util classes"
# 4. Auth feature
git add backend/src/handlers/AuthApi.java
git commit -m "feat(auth): signup, login, logout, /me endpoint"
# 5. Items feature
git add backend/src/handlers/ItemsApi.java backend/src/handlers/ClaimsApi.java
git commit -m "feat(items): CRUD for items and claims"
# 6. External APIs
git add backend/src/handlers/ExternalApi.java
git commit -m "feat(external): proxy Nominatim and UI Avatars"
# 7. Admin
git add backend/src/handlers/AdminApi.java
git commit -m "feat(admin): users list and activity logs endpoints"
# 8. Frontend
git add frontend/
git commit -m "feat(frontend): login, dashboard, admin pages + styles"
# 9. Push to GitHub (after creating an empty repo on GitHub)
git remote add origin https://github.com/<your-team>/<repo-name>.git
git push -u origin main

Tip: judges love seeing small, descriptive commits like fix:, feat:, docs:, refactor: — that proves real-time work and not a copy-paste of an old project.

8. Adapting on competition day
The skeleton has 4 building blocks you can reuse for almost any problem:

Auth + roles – usually the same code (AuthApi.java).
A list of "things" – rename items to whatever (events, requests, tickets, products, posts).
An action on a thing – rename claims to whatever (registrations, replies, applications, comments).
An admin view – usually the same code (AdminApi.java).
For example, if the problem is "Campus Helpdesk Ticketing" you would:

Rename table items → tickets, fields stay almost the same.
Rename claims → replies.
Add a priority column.
Most JavaScript and Java code stays the same – just rename the words and swap the form labels.
That is why you should practice running this project end to end before the competition.

9. Troubleshooting
Problem	Fix
ClassNotFoundException: com.mysql.cj.jdbc.Driver	The connector jar is missing. Check backend/lib/ has mysql-connector-j-*.jar and that you used -cp correctly.
Access denied for user 'root'@'localhost'	Wrong DB password in Database.java or env var.
Unknown database 'lost_and_found'	You did not run schema.sql. Run it inside the MySQL client.
Port 8080 already in use	Edit Main.java, change PORT = 8080 to e.g. 8081.
Page loads but no data	Open the browser dev tools → Network tab. The API call there shows the real error.
app.log not created	Make sure you run the java command from inside vibe-coding-prep/ so the relative path is right.
Good luck at CCE Week — go win it.