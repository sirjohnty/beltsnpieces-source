# Create: Belts n Pieces

An addon for the Create Mod adding features and changes focusing on belts and logistics

Requires: Minecraft 1.21.1, Java 21, NeoForge 21.1.206 or newer, Create 6.0.9 or newer.

## Additions
Reinforced belts providing more options for belt curves and heights

Belt voids to help with belts backing up

## Changes
Sneak interacting with a regular belt whilst holding a casing will encase the entire belt's length


## Integrations

Reinforced plates will use andesite sheets if an installed mod adds them, falling back to iron sheets if not

Create: Extra Casing - Reinforced belts can accept casings added by this mod

Create: The Factory Must Grow - Allows regular and reinforced belts to use Steel Casing

Create Transmission - Transmission chains connect automatically generate when connecting reinforced belts

## Config

Chain conveyor angles, belt lengths and other minor things can be tweaked in the mod's configs

## DISCLAIMERS

Ponder scenes are placeholder - they will have proper text written when I get the mod feature complete.

I want to make it very clear that I used AI tools to assist in the development of the mod - from performance testing, debugging as well as feature implementation.

I want to stress that I will NEVER use AI for generating mod descriptions, ponder text, any art assets (models, textures),

I made sure to set up my IDE, debugging client as well as a basic understanding of java. I didn't just "vibe" code this mod so if you have nothing nice to say about this fact when I have been completely transparent with my AI usage then don't comment at all please.





## Building from source

Install Java 21. This checkout uses NeoForge 21.1.259 through the Gradle wrapper.

Before compiling, supply these external dependency JARs (they are not included in Git):

- `libs/create-1.21.1-6.0.10.jar`
- `libs/flywheel-neoforge-1.21.1-1.0.6.jar`
- `libs/ponder-neoforge-1.0.82+mc1.21.1.jar`
- `libs/Registrate-MC1.21-1.3.0+67.jar`
- `dev-libs/createtransmission-1.2.2+neoforge-create6-1.21.1.jar`

Create Transmission is optional when playing the released mod, but its API is required to compile the compatibility code. Obtain these versions from their publishers. `setup-libraries.ps1 -SourceDirectory <folder>` can copy and check the four core libraries; place Create Transmission in `dev-libs` separately. No other optional integration mods or personal game saves are required to compile.

On Windows, run `.\gradlew.bat build`. On Linux/macOS, run `bash ./gradlew build`. Gradle downloads the remaining build dependencies. The release JAR is written to `build/libs/beltsnpieces-<version>-1.21.1-neoforge.jar`.

Generated build output, Gradle caches, IDE settings, dependency JARs, and the development game directory are ignored by Git. They do not belong in a source download. See `DEVELOPMENT.md` for optional local testing details.
