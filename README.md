# Mercury energy meter binding

# Биндинг openhab для счётчика Меркурий. Находится в разработке. Пожелания и предложения напрвляйте на почту или feature request.

_В данный момент рализован протокол обмена трёхфазных счетчиков Меркурий (Mercury) 203.2TD, 204, 208, 230, 231, 234, 236, 238_

## Supported Things
_Bridge_
```rs485``` - Мост для подключения к последовательному порту

_Thing_
``` energymeter203td ``` - Реализует протокол считывания данных

## Discovery
Пока не реализовано

## Thing Configuration
_Bridge_ - ```serialPort, portSpeed```\
```serialPort``` указывается обязательно\
```portSpeed``` по умолчанию 9600

_Thing_ energymeter203td - ```pollPeriod, userpassword```, имеют значения по умолчанию 60 секунд и 111111 соответственно

## Channels

| channel  | type   | description                  |
|----------|--------|------------------------------|
| voltage1(2,3)  | Number | напряжение на фазах  |
| current1(2,3)  | Number | сила тока на фазах  |
| power1(2,3)  | Number | мощность на фазах  |
| powertotal  | Number | мощность на всех фазах  |
| energyactive1(2,3)  | Number | расход энергии на фазах  |
| energyactivetotal  | Number | расход энергии на всех фазах  |


## Full Example
.things
```
Bridge mercuryenergymeter:rs485:rsBridge [serialPort="COM4", portSpeed=9600]{
Thing energymeter203td meter [pollPeriod=1]
}
```

.items
```
Number VoltageFase1 "Напряжение в 1 фазе" ["Point", "Voltage"]{ga="Sensor" [sensorName="Voltage"], channel="mercuryenergymeter:energymeter203td:rsBridge:meter:voltage1"}
Number VoltageFase2 "Напряжение в 2 фазе" ["Point", "Voltage"]{ga="Sensor" [sensorName="Voltage"], channel="mercuryenergymeter:energymeter203td:rsBridge:meter:voltage2"}
Number VoltageFase3 "Напряжение в 3 фазе" ["Point", "Voltage"]{ga="Sensor" [sensorName="Voltage"], channel="mercuryenergymeter:energymeter203td:rsBridge:meter:voltage3"}

Number CurrentFase1 "Сила тока в 1 фазе" ["Point", "Current"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:current1"}
Number CurrentFase1 "Сила тока в 2 фазе" ["Point", "Current"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:current2"}
Number CurrentFase1 "Сила тока в 3 фазе" ["Point", "Current"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:current3"}

Number TotalPower "Суммарная мощность по фазам" ["Point", "Power"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:powertotal"}
Number TotalPower1 "Мощность в 1 фазе" ["Point", "Power"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:power1"}
Number TotalPower2 "Мощность в 2 фазе" ["Point", "Power"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:power2"}
Number TotalPower3 "Мощность в 3 фазе" ["Point", "Power"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:power3"}

Number ActiveEnergyTotal "Общий расход активной энергии" ["Point", "Energy"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:energyactivetotal"}
Number ActiveEnergy1 "Расход активной энергии на 1 фазе" ["Point", "Energy"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:energyactive1"}
Number ActiveEnergy2 "Расход активной энергии на 2 фазе" ["Point", "Energy"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:energyactive2"}
Number ActiveEnergy3 "Расход активной энергии на 3 фазе" ["Point", "Energy"]{channel="mercuryenergymeter:energymeter203td:rsBridge:meter:energyactive3"}
```
