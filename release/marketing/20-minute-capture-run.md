# Теперь сделай вот эти 7 кадров в таком порядке

Перед стартом: открой `Thunderhead Showcase`, включи запись OBS, выполни `/tempestfx camera cinematic`, поставь FOV 50–55 и проверь, что HUD исчез. Каждый удар записывай с запасом 2 секунды до и 5 секунд после.

1. **Forest HERO — 3 минуты.** Встань в 20 блоках от центра тёмной лесной поляны, оставь 70% кадра небу. Выполни `/tempestfx strike-camera 20 --seed 12345`. Повтори 3 раза, не двигая камеру.
2. **Forest CLOSE IMPACT — 3 минуты.** Опустись ниже и подойди до 14 блоков. Выполни `/tempestfx strike-camera 14 --seed 12345`. Нужны core, sparks, ring и первый дым.
3. **Plains BRANCHING — 3 минуты.** Перейди на равнину, оставь 75% кадра небу, поставь удар на 80 блоков: `/tempestfx strike-camera 80 --seed 8273641`. Не обрежь верхние ветви.
4. **Water — 3 минуты.** Камера в 2–4 блоках над гладью, цель в 18–22 блоках. Наведи взгляд на воду и выполни `/tempestfx strike water --seed 45009`. Сними spray, steam и ripple.
5. **Village ILLUMINATION — 3 минуты.** Зафиксируй камеру на улице деревни. Сначала 2 секунды темноты, затем `/tempestfx strike-camera 30 --seed 71337`. Это даст before/after без смены экспозиции.
6. **Mountain SCALE + THUNDER — 4 минуты.** Камера через долину, вершина полностью видна. Ударь по точным координатам: `/tempestfx strike <x> <y> <z> --seed 99017`. После вспышки не двигайся и дождись грома.
7. **NO-SHADER PROOF — 3 минуты.** Без внешнего shaderpack вернись к Forest marker и повтори `/tempestfx strike-camera 20 --seed 12345`. Этот кадр должен честно доказать: **No external shader pack required.**

После последнего кадра выполни `/tempestfx camera off`. Из этих семи записей собери hero frame, close impact, branching, water, illumination split, delayed-thunder clip и no-shader proof. Shader beauty shot снимай только после отдельной проверки конкретных версий Iris и shaderpack.

