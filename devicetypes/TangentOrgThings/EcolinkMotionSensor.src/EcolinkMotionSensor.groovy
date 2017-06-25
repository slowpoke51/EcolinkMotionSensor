// vim :set ts=2 sw=2 sts=2 expandtab smarttab :
/**
 *  Ecolink Motion Sensor
 *
 *  Copyright 2016 Brian Aker
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

def getDriverVersion()
{
  return "v2.9"
}

def getAssociationGroup ()
{
  if ( zwaveHubNodeId == 1) {
    return 1
  }

  return 2
}

metadata {
  definition (name: "Ecolink PIR", namespace: "TangentOrgThings", author: "Brian Aker")
  {
    capability "Battery"
    capability "Configuration"
    capability "Motion Sensor"
    capability "Refresh"
    capability "Sensor"
    capability "Tamper Alert"

    // String attribute with name "firmwareVersion"
    attribute "driverVersion", "string"
    attribute "Associated", "enum", ["Unknown", "Yes", "No"]
    attribute "BasicReport", "enum", ["Unconfigured", "On", "Off"]
    attribute "MSR", "string"
    attribute "Manufacturer", "string"
    attribute "ManufacturerCode", "string"
    attribute "ProduceTypeCode", "string"
    attribute "ProductCode", "string"
    attribute "WakeUp", "string"
    attribute "WirelessConfig", "string"
    attribute "firmwareVersion", "string"

    // zw:S type:2001 mfr:014A prod:0001 model:0001 ver:2.00 zwv:3.40 lib:06 cc:30,71,72,86,85,84,80,70 ccOut:20
    fingerprint mfr: "014A", prod: "0001", model: "0001", deviceJoinName: "Ecolink Motion Sensor", inClusters: "0x30, 0x71, 0x72, 0x86, 0x85, 0x84, 0x80, 0x70", outClusters: "0x20" // Ecolink motion
    fingerprint mfr: "014A", prod: "0004", model: "0001", deviceJoinName: "Ecolink Motion Sensor"  // Ecolink motion +
  }

  simulator
  {
    status "inactive": "command: 3003, payload: 00"
    status "active": "command: 3003, payload: FF"
  }

  tiles
  {
    standardTile("motion", "device.motion", width: 2, height: 2)
    {
      state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
      state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
    }

    valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat")
    {
      state("battery", label:'${currentValue}', unit:"%")
    }

    valueTile("driverVersion", "device.driverVersion", inactiveLabel: true, decoration: "flat") 
    {
      state("driverVersion", label: getDriverVersion())
    }

    valueTile("associated", "device.Associated", inactiveLabel: false, decoration: "flat") 
    {
      state("device.Associated", label: '${currentValue}')
    }

    valueTile("tamper", "device.tamper", inactiveLabel: false, decoration: "flat") 
    {
      state "clear", backgroundColor:"#00FF00"
      state("detected", label: "detected", backgroundColor:"#e51426")
    }

    standardTile("configure", "device.switch", inactiveLabel: false, decoration: "flat")
    {
      state "default", label:"", action:"configuration.configure", icon:"st.secondary.configure"
    }

    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat")
    {
      state "default", label:'', action: "refresh.refresh", icon: "st.secondary.refresh"
    }

    main "motion"
    details(["motion", "battery", "tamper", "driverVersion", "configure", "associated", "refresh"])
  }
}

def parse(String description)
{
  def result = null

  if (description.startsWith("Err"))
  {
    if (description.startsWith("Err 106")) 
    {
      if (state.sec) {
        log.debug description
      } else {
        result = createEvent(
          descriptionText: "This device failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.",
          eventType: "ALERT",
          name: "secureInclusion",
          value: "failed",
          isStateChange: true,
        )
      }
    } else {
      result = createEvent(value: description, descriptionText: description)
    }
  } else if (description != "updated") {
    def cmd = zwave.parse(description)

    if (cmd)
    {
      result = zwaveEvent(cmd)

      if (!result)
      {
        log.warning "Parse Failed and returned ${result} for command ${cmd}"
        result = createEvent(value: description, descriptionText: description)
      }
    } else {
      log.info "Non-parsed event: ${description}"
      result = createEvent(value: description, descriptionText: description)
    }
  }

  return result
}

def sensorValueEvent(short value) {
  def result = []
  log.debug "sensorValueEvent: $value"

  if (value) {
    result << createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", isStateChange: true, displayed: true)
  } else {
    result << createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", isStateChange: true, displayed: true)
  }

  def cmds = []
  cmds.plus(setConfigured())
  if (cmds.size())
  {
    result << response(commands(setConfigured(), 1000))
  }

  return result
}

def setConfigured() {
  def cmds = []

  if (device.currentValue("BasicReport") != "On") {
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: [0xFF], size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 0x63)
  }

  if (device.currentValue("Associated") != "Yes") {
    cmds << zwave.associationV2.associationSet(groupingIdentifier: getAssociationGroup(), nodeId:[zwaveHubNodeId])
      cmds << zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup())
  }

  if (device.currentValue("MSR") == null) {
    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
  }

  if (device.currentValue("firmwareVersion") == null) {
    cmds << zwave.versionV1.versionGet()
  }

  return cmds
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
  sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
  sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv2.AlarmReport cmd, results) {
  def result = []

  if (cmd.alarmLevel == 0x11) {
    result << createEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName covering was removed", isStateChange: true, displayed: true)
  } else {
    result << createEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName is clear", isStateChange: true, displayed: true)
  }

  return result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
  def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]

  if (state.tamper == "clear") {
    result << createEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName is clear", isStateChange: true, displayed: true)
  }

  // If the device is in the process of configuring a newly joined network, do not send wakeUpnoMoreInformation commands
  def cmds = []
  cmds.plus(setConfigured())

  if (isConfigured()) {
    if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
      cmds << zwave.batteryV1.batteryGet()
    } else if (getAssociationGroup() == 1 && cmds.size() == 0) {
      // If Smartthings it Primary Controller then it is ok to tell the device to go to sleep.
      result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
    }
  }

  if (cmds.size())
  {
    result << response(commands(cmds, 1000))
  }

  return result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
  def map = [ name: "battery", unit: "%" ]

  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "${device.displayName} has a low battery"
    map.isStateChange = true
    map.displayed = true
  } else {
    map.value = batteryLevelcmd.batteryLevel

    if (state.previous_batteryLevel != batteryLevelcmd.batteryLevel) {
      state.previous_batteryLevel = batteryLevelcmd.batteryLevel
      map.isStateChange = true
      map.displayed = true
    }
    map.descriptionText = "${device.displayName} is at ${batteryLevelcmd.batteryLevel}%"
  }

  state.lastbat = new Date().time

  def result = [createEvent(map)]

  if (device.currentValue("Associated") != "On") {
    result << response(commands([
      zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: 0xFF, size: 1),
      zwave.batteryV1.batteryGet()
    ]))
  }

  return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
  createEvent(descriptionText: "$device.displayName command not implemented: $cmd", displayed: true)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) 
{
  def result = []

  def manufacturerCode = String.format("%04X", cmd.manufacturerId)
  def productTypeCode = String.format("%04X", cmd.productTypeId)
  def productCode = String.format("%04X", cmd.productId)
  def wirelessConfig = "ZWAVE"

  result << createEvent(name: "ManufacturerCode", value: manufacturerCode)
  result << createEvent(name: "ProduceTypeCode", value: productTypeCode)
  result << createEvent(name: "ProductCode", value: productCode)
  result << createEvent(name: "WirelessConfig", value: wirelessConfig)

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)	updateDataValue("MSR", msr)
  updateDataValue("manufacturer", cmd.manufacturerName)
  if (!state.manufacturer) {
    state.manufacturer= cmd.manufacturerName
  }

  result << createEvent([name: "MSR", value: "$msr", descriptionText: "$device.displayName", isStateChange: false])
  result << createEvent([name: "Manufacturer", value: "${cmd.manufacturerName}", descriptionText: "$device.displayName", isStateChange: false])

  return result
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
  def text = "$device.displayName: firmware version: ${cmd.applicationVersion}.${cmd.applicationSubVersion}, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
  createEvent([name: "firmwareVersion", value: "${cmd.applicationVersion}.${cmd.applicationSubVersion}", descriptionText: "$text", isStateChange: false])
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
  def result = []
  Boolean needs_configuring = true

  if (cmd.parameterNumber == 0x63) {
    if (cmd.configurationValue == 0xFF)
    {
      result << createEvent(name: "BasicReport", value: "On", displayed: false)
      needs_configuring = false
    } else {
      result << createEvent(name: "BasicReport", value: "Off", displayed: false)
    }
  } else {
    result << createEvent(name: "BasicReport", value: "Unconfigured", displayed: false)
  }

  if (needs_configuring) {
    result << response(commands([
      zwave.configurationV1.configurationSet(parameterNumber: 0x63, configurationValue: [0xFF], size: 1),
      zwave.configurationV1.configurationGet(parameterNumber: 0x63)
    ], 1000))
  }

  return result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
  def result = []
  Boolean misconfigured = true

  if (cmd.groupingIdentifier == getAssociationGroup()) {
    if (cmd.nodeId.any { it == zwaveHubNodeId }) 
    {
      def string_of_assoc
      cmd.nodeId.each {
        string_of_assoc << "${it}, "
      }
      def lengthMinus2 = string_of_assoc.length() - 2
      def final_string = string_of_assoc.getAt(0..lengthMinus2)
      result << createEvent(name: "Associated",
      value: "Yes", 
      descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier} : ${final_string}",
      displayed: true,
      isStateChange: true)

      misconfigured = false
    }
  } else if (cmd.groupingIdentifier != getAssociationGroup()) {
    result << createEvent(name: "Associated",
    value: "No",
    descriptionText: "$device.displayName is misconfigured for group ${cmd.groupingIdentifier}",
    displayed: true,
    isStateChange: true)
  } else {
    result << createEvent(name: "Associated",
    value: "No",
    descriptionText: "$device.displayName is not associated in group ${cmd.groupingIdentifier}",
    displayed: true,
    isStateChange: true)
  }

  if (misconfigured) {
    result << response(commands([
      zwave.associationV2.associationSet(groupingIdentifier: getAssociationGroup(), nodeId:[zwaveHubNodeId]),
      zwave.associationV2.associationGet(groupingIdentifier: getAssociationGroup())
    ], 1000))
  }

  return result
}

def refresh() {
  def cmds = [
    zwave.batteryV1.batteryGet(),
    zwave.alarmV2.AlarmGet(),
    zwave.configurationV1.configurationGet(parameterNumber: 0x63),
    zwave.associationV2.associationGet(groupingIdentifier:2)
  ]

  if (getDataValue("MSR") == null) {
    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
  }

  if (getDataValue('fw') == null) {
    cmds << zwave.versionV1.versionGet()
  }

  response(commands(cmds, 1000))
}

def updated() {
  log.debug "$device.displayName updated"
  sendEvent(name: "Associated", value: "Unknown", displayed: true, isStateChange: true)
  sendEvent(name: "BasicReport", value: "Unknown", displayed: true, isStateChange: true)
  response(commands(setConfigured()))
}

def installed() {
  log.debug "$device.displayName installed"
  def cmds = []

  cmds = [
  zwave.manufacturerSpecificV1.manufacturerSpecificGet(),
    zwave.versionV1.versionGet()
]
cmds.plus(setConfigured())

  return response(commands(cmds))
}

def setConfigured() {
	Boolean Group1 = device.getDataValue(["Group1"]) as Boolean
	Boolean Group2 = device.getDataValue(["Group2"]) as Boolean
	if ( Group1 && Group2 )
	{
		device.updateDataValue("configured", "true")
	}
	else
	{
		device.updateDataValue("configured", "false")
	}
}

def configure() {
  response(commands(setConfigured(), 1000))
}

def isConfigured() {
  if (device.currentValue("BasicReport") == "On" &&
      device.currentValue("Associated") == "Yes" &&
      device.currentValue("MSR") != null &&
      device.currentValue("firmwareVersion") != null) {
    return true
      }

  return false
}

private command(physicalgraph.zwave.Command cmd) {
  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private commands(commands, delay=200) {
  delayBetween(commands.collect{ command(it) }, delay)
}
