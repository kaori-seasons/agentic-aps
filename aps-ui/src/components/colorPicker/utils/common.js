import tinycolor from "tinycolor2";
/**
 * 转换为颜色序列数组
 */
export function transColorArr (value) {
  let arr = []
  if (!value.startsWith('linear-gradient(')) {
    arr.push({
      color: tranRenderColor(value),
      offset: 0,
      alpha: 100
    })
  } else {
    const colorArr = analysGradual (value)
    colorArr.map((item) => {
      arr.push({
        color: item.renderColor,
        offset: parseFloat(item.left / 100),
        alpha: item.alpha
      })
    })
  }
  return arr
}

/**
 * 将hex颜色转换为对应的显示颜色
 * @param {*} color 颜色
 * @param {*} alpha 透明度
 * @param {*} colorType 
 * @param {*} type 
 */
export function byColorTypeAndTypeTransColor (color, alpha, colorType, type) {
  let colorList
  if (colorType == 'hex') {
    return color + decToHex(alpha)
  } else {
    switch (type) {
      case "hsl":
        colorList = tinycolor(color).toHsl()
        return `hsla(${colorList.h},${colorList.s},${colorList.l},${alpha}%)`
        break
      case "hsb":
        colorList = tinycolor(color).toHsv()
        return `hsba(${colorList.h},${colorList.s},${colorList.v},${alpha}%)`
        break
      default:
        colorList = getAlpha(tinycolor(color).toRgbString())
        return `rgba(${colorList[0]}, ${colorList[1]}, ${colorList[2]}, ${alpha}%)`
        break
    }
  }
}

export function getLastColor (val) {
  if (!val) return val
  if (!val.startsWith('linear-gradient(')) {
    return val
  } else {
    const arr = analysGradual(val)
    const newArr = arr.sort((a, b) => {
      return a.left - b.left
    })
    const colorItem = newArr[newArr.length - 1]
    let str = ''
    if (colorItem.color.startsWith('#')) {
      let itemAlpha = colorItem.alpha
      if (itemAlpha == 100) {
        str += colorItem.color
      } else {
        str += colorItem.color + decToHex(itemAlpha)
      }
    } else {
      str += colorItem.color
    }
    return str
  }
}

/**
 * 转换渲染颜色
 */
export function tranRenderColor (value, setAlpha) {
  let str = ''
  value = value || '#000000'
  if (!value.startsWith('linear-gradient(')) {
    if (value.startsWith('#')) {
      const getHexObj = getHexColorAlpha(value)
      let hexAlpha = getHexObj.alpha
      if (setAlpha) {
        if (hexAlpha > setAlpha) {
          hexAlpha = setAlpha
        } else {
          hexAlpha = 0
        }
      }
      str = transColor(getHexObj.color, hexAlpha).renderColor
    } else {
      const colorCssArr = getAlpha(value)
      let copyModelVal = value
      if (copyModelVal.substr(0, 3) == 'hsb') {
        copyModelVal = tinycolor({ h: colorCssArr[0], s: colorCssArr[1], v: colorCssArr[2] }).toHexString()
      }
      let alpha = colorCssArr[3]
      if (!alpha && alpha != 0) {
        alpha = 100
      }
      if (setAlpha) {
        if (alpha > setAlpha) {
          alpha = setAlpha
        } else {
          alpha = 0
        }
      }
      str = transColor(copyModelVal, alpha).renderColor
    }
  } else {
    const directionRegex = /linear-gradient\(([^,]+)/;
    const directionMatch = directionRegex.exec(value);
    const direction = parseInt(directionMatch ? directionMatch[1].trim() : '0');
    str = `linear-gradient(${direction}deg, `;
    const arr = analysGradual(value)
    arr.sort((a, b) => {
      return a.left - b.left
    }).map((item, index) => {
      if (index != 0) {
        str += ','
      }
      if (item.color.startsWith('#')) {
        let itemAlpha = item.alpha
        if (setAlpha) {
          if (itemAlpha > setAlpha) {
            itemAlpha = setAlpha
          } else {
            itemAlpha = 0
          }
        }
        if (itemAlpha == 100) {
          str += item.color
        } else {
          str += item.color + decToHex(itemAlpha)
        }
      } else {
        str += item.color
      }
      str += ' ' + item.left + '%'
    })
    str += ')'
  }
  return str
}

/**
 * 解析渐变的颜色组
 * @param {*} value 
 * @returns 
 */
export function analysGradual (value) {
  const arr = []
  const startIndex = value.indexOf('deg')
  let colorArr
  if (startIndex != -1) {
    colorArr = value.substring(startIndex + 5, value.length - 2).split('%,')
  } else {
    colorArr = value.split('%,')
  }
  colorArr.map((item, index) => {
    let newItem = item, alpha, left, color;
    if(item.startsWith('#')) {
      let getHexObj = getHexColorAlpha(item)
      newItem = getHexObj.color
      color = getHexObj.color
      alpha = getHexObj.alpha
      left = item.split(' ')[1] || 0
    }else {
      const rgbaArr = getAlpha(item)
      alpha = rgbaArr[3]
      if(!alpha && alpha != 0) {
        alpha = 100
      }
      left = parseInt(item.split(') ')[1]) || 0
      if (item.split(') ')[0].endsWith(')')) {
        color = item.split(') ')[0]
      } else {
        color = item.split(') ')[0] + ')'
      }
    }
    const { renderColor } = transColor(newItem, alpha)
    arr.push({
      id: generateUUID(),
      color: color,
      renderColor: renderColor,
      left: left,
      alpha: alpha,
    })
  })
  return arr
}

/**
 * 颜色转换为RGB
 */
export function transColor (value, alpha) {
  let renderColor = value, renderColor1 = value, colorList
  if (value.startsWith('#')) {
    colorList = getAlpha(tinycolor(value).toRgbString())
  } else {
    const newArr = getAlpha(value)
    switch (value.substr(0, 3)) {
      case "hsl":
        colorList = getAlpha(tinycolor({ h: newArr[0], s: newArr[1], l: newArr[2] }).toRgbString())
        break
      case "hsb":
        colorList = getAlpha(tinycolor({ h: newArr[0], s: newArr[1], v: newArr[2] }).toRgbString())
        break
      default:
        colorList = getAlpha(tinycolor({ r: newArr[0], g: newArr[1], b: newArr[2] }).toRgbString())
        break
    }
  }
  renderColor = `rgba(${colorList[0]}, ${colorList[1]}, ${colorList[2]}, ${alpha}%)`
  renderColor1 = `rgba(${colorList[0]}, ${colorList[1]}, ${colorList[2]}, 0%)`
  return { renderColor, renderColor1 }
}
/**
 * 获取传入颜色值的数组得到RGBA或色块饱和度等
 * @param {*} value
 */
export function getAlpha (value) {
  if (!value) return
  // 取出括号中间的值
  const match = value.match(/\(([^)]+)\)/);
  if (match) {
    const valuesStr = match[1];
    const valuesArr = valuesStr.split(',').map((value, index) => {
      if (index == 3) {
        let valueTrim = value.trim().replace('%', '')
        return parseFloat(valueTrim) * (value.includes('%') ? 1 : 100)
      } else {
        let valueTrim = value.trim().replace('%', '').replace('deg', '')
        return parseFloat(valueTrim)
      }
    });
    return valuesArr.map(Number);
  }
  return []
}
/**
 * 获取hex的颜色和透明度
 * @param {*} item 
 */
export function getHexColorAlpha (item) {

  let color = item.split(' ')[0].substring(0, 7), alpha
  if (item.split(' ')[0].length <= 7) {
    alpha = 100
  } else {
    alpha = hexToDec(item.split(' ')[0].substring(7))
  }
  return { color, alpha }
}

/**
 * 16进制转十进制
 */
export function hexToDec (hex) {
  if (!/^[0-9a-fA-F]{2}$/.test(hex)) {
    throw new Error("Invalid hexadecimal input. Must be two characters.");
  }
  return parseInt(hex, 16);
}

/**
 * 十进制转16进制
 */
export function decToHex (dec) {
  if(dec > 100) {
    dec = 100
  }
  if(dec < 0 || dec > 100) {
    throw new Error("Input must be between 0 and 100.");
  }
  return dec.toString(16).padStart(2, '0');
}

// 生成uuid
export function generateUUID () {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}