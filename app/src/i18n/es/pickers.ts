export const pickersText = {
  choose: 'Elegir',
  close: 'Listo',
  spokenTime: (hours: number, minutes: number) => {
    const hoursText = `${hours} ${hours === 1 ? 'hora' : 'horas'}`;
    if (minutes === 0) {
      return hoursText;
    }
    return `${hoursText} y ${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}`;
  },
};
