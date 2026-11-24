"""The exact startup notice reproduced with the pinned BOP development jars."""

import re

BOP_SPAWN_CONTROL_HASHES = frozenset({
    "5F3C8D752C2E0464ECA8C9527D8FFE498129FD37A2FE86A266B64C98371763B1",
    "0C49B5EF447A7F09100E9A210888A7347FBDA0AA75322063991F6063B25FE3F9",
    "59D2A3FB3D6877E43018FD6A2B199D526074A864276A5F49091409A1CDC62FAE",
})


def has_controlled_bop_inputs(vendor_inputs):
    return BOP_SPAWN_CONTROL_HASHES <= set(vendor_inputs)


BOP_ALLAY_MESSAGE = (
    "The following entities have not registered to the RegisterSpawnPlacementsEvent, "
    "but a spawn entry was found. This will mean that the entity doesn't have restrictions "
    "on its spawn location, please register a spawn placement for the entity, you can "
    "register with NO_RESTRICTIONS if you don't want any restrictions."
)
BOP_ALLAY_NOTICE = re.compile(
    r"(?m)^\[[^\r\n]+\] \[Server thread/ERROR\] \["
    r"(?:net\.neoforged\.neoforge\.server|ne\.ne\.ne\.se)\.ServerLifecycleHooks/\]: "
    + re.escape(BOP_ALLAY_MESSAGE) + r"\n\t - minecraft:allay\n(?![ \t]+-)"
)


def checked_bop_development_text(text):
    """Validate the known startup notice without changing the saved log."""
    text = text.replace("\r\n", "\n")
    notices = list(BOP_ALLAY_NOTICE.finditer(text))
    if len(notices) > 1:
        raise ValueError("Repeated BOP allay spawn diagnostic")
    if notices:
        notice = notices[0]
        before = text[:notice.start()]
        if "Done (" in before or "Preparing start region for dimension " in before:
            raise ValueError("BOP allay spawn diagnostic appeared after startup")
        text = text[:notice.start()] + text[notice.end():]
    return text
